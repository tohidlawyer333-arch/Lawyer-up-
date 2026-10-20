using System.Net;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Windows;

namespace Payesh.Desktop;

public partial class MainWindow : Window
{
    private readonly string _dataDir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Payesh");
    private long _remoteRevision;
    private string LocalFile => Path.Combine(_dataDir, "workspace.enc");

    public MainWindow()
    {
        InitializeComponent();
        Directory.CreateDirectory(_dataDir);
        if (File.Exists(LocalFile)) StatusText.Text = "نسخه رمزگذاری‌شده محلی پیدا شد. عبارت رمزگذاری را وارد کنید و ذخیره امن را بزنید تا محتوای جدید رمزگذاری شود.";
    }

    private async void SaveLocal_Click(object sender, RoutedEventArgs e)
    {
        try
        {
            var pass = PassphraseBox.Password;
            if (pass.Length < 12) throw new InvalidOperationException("عبارت رمزگذاری باید حداقل ۱۲ نویسه باشد.");
            if (string.IsNullOrWhiteSpace(WorkspaceBox.Text) && File.Exists(LocalFile))
            {
                WorkspaceBox.Text = Encoding.UTF8.GetString(Decrypt(await File.ReadAllBytesAsync(LocalFile), pass));
                WorkspaceBox.IsReadOnly = IsPayeshWorkspace(WorkspaceBox.Text);
                StatusText.Text = "فضای کاری محلی با موفقیت رمزگشایی شد.";
                return;
            }
            if (WorkspaceBox.Text.Length == 0) throw new InvalidOperationException("ابتدا محتوای فضای کاری را وارد کنید.");
            await File.WriteAllBytesAsync(LocalFile, Encrypt(Encoding.UTF8.GetBytes(WorkspaceBox.Text), pass));
            StatusText.Text = "فضای کاری به‌صورت رمزگذاری‌شده در پروفایل همین کاربر ویندوز ذخیره شد.";
        }
        catch (Exception ex) { StatusText.Text = "ذخیره انجام نشد: " + SafeMessage(ex); }
    }

    private async void SyncButton_Click(object sender, RoutedEventArgs e)
    {
        SyncButton.IsEnabled = false;
        try
        {
            var baseUrl = ServerUrlBox.Text.Trim().TrimEnd('/');
            if (!Uri.TryCreate(baseUrl, UriKind.Absolute, out var uri) || (uri.Scheme != Uri.UriSchemeHttps && !uri.IsLoopback))
                throw new InvalidOperationException("برای همگام‌سازی، HTTPS الزامی است؛ فقط localhost برای آزمون محلی مجاز است.");
            var user = UserIdBox.Text.Trim();
            var token = TokenBox.Password;
            var pass = PassphraseBox.Password;
            if (user.Length is < 1 or > 100 || token.Length < 24 || pass.Length < 12)
                throw new InvalidOperationException("شناسه کاربری، توکن دسترسی و عبارت رمزگذاری را بررسی کنید.");
            using var client = new HttpClient { BaseAddress = new Uri(baseUrl + "/"), Timeout = TimeSpan.FromSeconds(20) };
            client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", token);
            client.DefaultRequestHeaders.Add("X-Payesh-User", user);

            var localText = WorkspaceBox.Text;
            var response = await client.GetAsync("v1/sync/workspace");
            if (response.StatusCode == HttpStatusCode.NotFound)
            {
                if (string.IsNullOrWhiteSpace(localText) && File.Exists(LocalFile))
                    localText = Encoding.UTF8.GetString(Decrypt(await File.ReadAllBytesAsync(LocalFile), pass));
                if (string.IsNullOrWhiteSpace(localText))
                    throw new InvalidOperationException("نسخه محلی و ابری وجود ندارد؛ ابتدا همگام‌سازی اولیه را از برنامه اندروید انجام دهید.");
                if (!IsPayeshWorkspace(localText))
                    throw new InvalidOperationException("برای همگام‌سازی، فقط قالب رسمی خروجی دفتر پایش (format=1) پذیرفته می‌شود؛ متن آزاد همگام نمی‌شود.");
                var created = await PutWorkspace(client, 0, Encrypt(Encoding.UTF8.GetBytes(localText), pass));
                _remoteRevision = created.Revision;
                WorkspaceBox.Text = localText;
                await File.WriteAllBytesAsync(LocalFile, Encrypt(Encoding.UTF8.GetBytes(localText), pass));
                StatusText.Text = $"فضای کاری به‌صورت رمزگذاری‌شده ارسال شد (نسخه {_remoteRevision}).";
                return;
            }
            response.EnsureSuccessStatusCode();
            var remote = await response.Content.ReadFromJsonAsync<SyncEnvelope>()
                ?? throw new InvalidOperationException("پاسخ سرویس قابل خواندن نیست.");
            _remoteRevision = remote.Revision;
            var remoteText = Encoding.UTF8.GetString(Decrypt(Convert.FromBase64String(remote.Payload), pass));
            if (!IsPayeshWorkspace(remoteText))
                throw new InvalidDataException("محتوای ابری قالب دفتر پایش نیست؛ هیچ داده‌ای بازنویسی نشد.");

            if (string.IsNullOrWhiteSpace(localText) && File.Exists(LocalFile))
                localText = Encoding.UTF8.GetString(Decrypt(await File.ReadAllBytesAsync(LocalFile), pass));

            if (string.IsNullOrWhiteSpace(localText))
            {
                WorkspaceBox.Text = remoteText;
                WorkspaceBox.IsReadOnly = true;
                await File.WriteAllBytesAsync(LocalFile, Encrypt(Encoding.UTF8.GetBytes(remoteText), pass));
                StatusText.Text = $"نسخه ابری دریافت شد (نسخه {_remoteRevision}).";
                return;
            }
            if (string.Equals(localText, remoteText, StringComparison.Ordinal))
            {
                WorkspaceBox.IsReadOnly = true;
                StatusText.Text = $"نسخه محلی و ابری یکسان‌اند (نسخه {_remoteRevision}).";
                return;
            }

            var choice = MessageBox.Show(
                "محتوای محلی و ابری متفاوت است. بله: دریافت نسخه ابری؛ خیر: ارسال نسخه محلی؛ لغو: هیچ تغییری نده.",
                "تعارض همگام‌سازی", MessageBoxButton.YesNoCancel, MessageBoxImage.Warning);
            if (choice == MessageBoxResult.Cancel)
            {
                StatusText.Text = "تعارض شناسایی شد؛ هیچ نسخه‌ای بازنویسی نشد.";
                return;
            }
            if (choice == MessageBoxResult.Yes)
            {
                WorkspaceBox.Text = remoteText;
                WorkspaceBox.IsReadOnly = true;
                await File.WriteAllBytesAsync(LocalFile, Encrypt(Encoding.UTF8.GetBytes(remoteText), pass));
                StatusText.Text = $"نسخه ابری دریافت شد (نسخه {_remoteRevision}).";
                return;
            }

            var updated = await PutWorkspace(client, _remoteRevision, Encrypt(Encoding.UTF8.GetBytes(localText), pass));
            _remoteRevision = updated.Revision;
            WorkspaceBox.Text = localText;
            WorkspaceBox.IsReadOnly = true;
            await File.WriteAllBytesAsync(LocalFile, Encrypt(Encoding.UTF8.GetBytes(localText), pass));
            StatusText.Text = $"نسخه محلی به‌صورت رمزگذاری‌شده ارسال شد (نسخه {_remoteRevision}).";
        }
        catch (Exception ex) { StatusText.Text = "همگام‌سازی انجام نشد: " + SafeMessage(ex); }
        finally { SyncButton.IsEnabled = true; }
    }

    private static async Task<SyncEnvelope> PutWorkspace(HttpClient client, long expectedRevision, byte[] encryptedPayload)
    {
        var payload = Convert.ToBase64String(encryptedPayload);
        using var response = await client.PutAsJsonAsync("v1/sync/workspace", new { expectedRevision, payload });
        if (response.StatusCode == HttpStatusCode.Conflict)
            throw new InvalidOperationException("نسخه ابری هم‌زمان تغییر کرده است؛ دوباره دریافت و تعارض را بررسی کنید.");
        response.EnsureSuccessStatusCode();
        return await response.Content.ReadFromJsonAsync<SyncEnvelope>()
            ?? throw new InvalidOperationException("پاسخ ذخیره‌سازی سرویس قابل خواندن نیست.");
    }

    private static bool IsPayeshWorkspace(string text)
    {
        try
        {
            using var document = JsonDocument.Parse(text);
            var root = document.RootElement;
            return root.ValueKind == JsonValueKind.Object &&
                root.TryGetProperty("format", out var format) && format.GetInt32() == 1 &&
                root.TryGetProperty("records", out var records) && records.ValueKind == JsonValueKind.Array &&
                root.TryGetProperty("tasks", out var tasks) && tasks.ValueKind == JsonValueKind.Array &&
                records.GetArrayLength() <= 20_000 && tasks.GetArrayLength() <= 50_000;
        }
        catch { return false; }
    }

    private static byte[] Encrypt(byte[] plain, string passphrase)
    {
        var salt = RandomNumberGenerator.GetBytes(16);
        var nonce = RandomNumberGenerator.GetBytes(12);
        var key = Rfc2898DeriveBytes.Pbkdf2(passphrase, salt, 600_000, HashAlgorithmName.SHA256, 32);
        var cipher = new byte[plain.Length];
        var tag = new byte[16];
        using (var aes = new AesGcm(key, 16)) aes.Encrypt(nonce, plain, cipher, tag, Encoding.UTF8.GetBytes("PAYESH-SYNC-V1"));
        CryptographicOperations.ZeroMemory(key);
        return Encoding.ASCII.GetBytes("PAYESHE1").Concat(salt).Concat(nonce).Concat(tag).Concat(cipher).ToArray();
    }

    private static byte[] Decrypt(byte[] bytes, string passphrase)
    {
        if (bytes.Length < 52 || Encoding.ASCII.GetString(bytes, 0, 8) != "PAYESHE1") throw new InvalidDataException("قالب رمزگذاری ناشناخته است.");
        var salt = bytes.AsSpan(8, 16).ToArray(); var nonce = bytes.AsSpan(24, 12).ToArray();
        var tag = bytes.AsSpan(36, 16).ToArray(); var cipher = bytes.AsSpan(52).ToArray();
        var key = Rfc2898DeriveBytes.Pbkdf2(passphrase, salt, 600_000, HashAlgorithmName.SHA256, 32);
        var plain = new byte[cipher.Length];
        try { using var aes = new AesGcm(key, 16); aes.Decrypt(nonce, cipher, tag, plain, Encoding.UTF8.GetBytes("PAYESH-SYNC-V1")); return plain; }
        finally { CryptographicOperations.ZeroMemory(key); }
    }

    private static string SafeMessage(Exception ex) =>
        ex is HttpRequestException ? "اتصال به سرویس برقرار نشد؛ نشانی و گواهی TLS را بررسی کنید." :
        ex is CryptographicException ? "رمزگشایی ناموفق بود؛ عبارت رمزگذاری را بررسی کنید. اطلاعات بازنویسی نشد." : ex.Message;

    private sealed record SyncEnvelope(long Revision, string Payload);
}
