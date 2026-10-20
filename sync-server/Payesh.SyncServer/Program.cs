using System.Security.Cryptography;
using System.Text;
using System.Threading.RateLimiting;
using Microsoft.AspNetCore.RateLimiting;
using Microsoft.Data.Sqlite;

var builder = WebApplication.CreateBuilder(args);
builder.WebHost.ConfigureKestrel(options => options.Limits.MaxRequestBodySize = 15 * 1024 * 1024);
builder.Services.AddRateLimiter(options =>
{
    options.RejectionStatusCode = StatusCodes.Status429TooManyRequests;
    options.AddPolicy("sync", http => RateLimitPartition.GetFixedWindowLimiter(
        http.Request.Headers["X-Payesh-User"].ToString(),
        _ => new FixedWindowRateLimiterOptions
        {
            PermitLimit = 30,
            Window = TimeSpan.FromMinutes(1),
            QueueLimit = 0,
            AutoReplenishment = true
        }));
});
var tokenConfig = Environment.GetEnvironmentVariable("PAYESH_SYNC_TOKENS") ?? "";
var tokenMap = tokenConfig.Split(';', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries)
    .Select(entry => entry.Split('=', 2))
    .Where(parts => parts.Length == 2 && parts[0].Length is > 0 and <= 100 && parts[1].Length >= 24)
    .ToDictionary(parts => parts[0], parts => SHA256.HashData(Encoding.UTF8.GetBytes(parts[1])), StringComparer.Ordinal);
if (tokenMap.Count == 0) throw new InvalidOperationException("Configure PAYESH_SYNC_TOKENS as userId=long-random-token;user2=another-token before starting the service.");
var databasePath = Environment.GetEnvironmentVariable("PAYESH_SYNC_DB") ?? Path.Combine(AppContext.BaseDirectory, "payesh-sync.db");
Directory.CreateDirectory(Path.GetDirectoryName(Path.GetFullPath(databasePath))!);
var connectionString = new SqliteConnectionStringBuilder { DataSource = databasePath, Mode = SqliteOpenMode.ReadWriteCreate, Cache = SqliteCacheMode.Shared }.ToString();
await using (var db = new SqliteConnection(connectionString))
{
    await db.OpenAsync();
    await using var cmd = db.CreateCommand();
    cmd.CommandText = "PRAGMA journal_mode=WAL; CREATE TABLE IF NOT EXISTS workspace(user_id TEXT PRIMARY KEY, revision INTEGER NOT NULL, payload TEXT NOT NULL, updated_utc TEXT NOT NULL);";
    await cmd.ExecuteNonQueryAsync();
}
var writeLock = new SemaphoreSlim(1, 1);
var app = builder.Build();
app.UseRateLimiter();
app.MapGet("/health", () => Results.Ok(new { status = "ok", service = "payesh-sync" }));
app.MapGet("/v1/sync/workspace", async (HttpContext http) =>
{
    if (!TryUser(http, tokenMap, out var userId)) return Results.Unauthorized();
    await using var db = new SqliteConnection(connectionString);
    await db.OpenAsync();
    await using var cmd = db.CreateCommand();
    cmd.CommandText = "SELECT revision, payload FROM workspace WHERE user_id = $user";
    cmd.Parameters.AddWithValue("$user", userId);
    await using var reader = await cmd.ExecuteReaderAsync();
    if (!await reader.ReadAsync()) return Results.NotFound();
    return Results.Ok(new WorkspaceResponse(reader.GetInt64(0), reader.GetString(1)));
}).RequireRateLimiting("sync");
app.MapPut("/v1/sync/workspace", async (HttpContext http, SyncWrite body) =>
{
    if (!TryUser(http, tokenMap, out var userId)) return Results.Unauthorized();
    byte[] encryptedBytes;
    try { encryptedBytes = Convert.FromBase64String(body.Payload ?? ""); }
    catch (FormatException) { return Results.BadRequest(new { error = "invalid_encrypted_payload" }); }
    if (encryptedBytes.Length is < 52 or > 10_000_000 || Encoding.ASCII.GetString(encryptedBytes, 0, 8) != "PAYESHE1")
        return Results.BadRequest(new { error = "invalid_encrypted_payload" });
    if (body.ExpectedRevision < 0) return Results.BadRequest(new { error = "invalid_revision" });
    await writeLock.WaitAsync();
    try
    {
        await using var db = new SqliteConnection(connectionString);
        await db.OpenAsync();
        await using var tx = await db.BeginTransactionAsync();
        long currentRevision = 0;
        var exists = false;
        await using (var read = db.CreateCommand())
        {
            read.Transaction = (SqliteTransaction)tx;
            read.CommandText = "SELECT revision FROM workspace WHERE user_id = $user";
            read.Parameters.AddWithValue("$user", userId);
            var result = await read.ExecuteScalarAsync();
            if (result is not null && result is not DBNull) { currentRevision = Convert.ToInt64(result); exists = true; }
        }
        if (currentRevision != body.ExpectedRevision || (!exists && body.ExpectedRevision != 0))
        {
            await tx.RollbackAsync();
            return Results.Conflict(new { error = "revision_conflict", currentRevision });
        }
        var nextRevision = currentRevision + 1;
        await using (var write = db.CreateCommand())
        {
            write.Transaction = (SqliteTransaction)tx;
            write.CommandText = "INSERT INTO workspace(user_id, revision, payload, updated_utc) VALUES($user, $revision, $payload, $updated) ON CONFLICT(user_id) DO UPDATE SET revision=excluded.revision, payload=excluded.payload, updated_utc=excluded.updated_utc";
            write.Parameters.AddWithValue("$user", userId);
            write.Parameters.AddWithValue("$revision", nextRevision);
            write.Parameters.AddWithValue("$payload", body.Payload);
            write.Parameters.AddWithValue("$updated", DateTimeOffset.UtcNow.ToString("O"));
            await write.ExecuteNonQueryAsync();
        }
        await tx.CommitAsync();
        return Results.Ok(new WorkspaceResponse(nextRevision, body.Payload));
    }
    finally { writeLock.Release(); }
}).RequireRateLimiting("sync");
app.Run();

static bool TryUser(HttpContext context, IReadOnlyDictionary<string, byte[]> tokenMap, out string userId)
{
    userId = "";
    var suppliedUser = context.Request.Headers["X-Payesh-User"].ToString();
    var authorization = context.Request.Headers.Authorization.ToString();
    if (suppliedUser.Length is < 1 or > 100 || !authorization.StartsWith("Bearer ", StringComparison.Ordinal)) return false;
    if (!tokenMap.TryGetValue(suppliedUser, out var expected)) return false;
    var token = authorization[7..].Trim();
    if (token.Length is < 24 or > 512) return false;
    var actual = SHA256.HashData(Encoding.UTF8.GetBytes(token));
    if (!CryptographicOperations.FixedTimeEquals(actual, expected)) return false;
    userId = suppliedUser;
    return true;
}

public sealed record SyncWrite(long ExpectedRevision, string Payload);
public sealed record WorkspaceResponse(long Revision, string Payload);
