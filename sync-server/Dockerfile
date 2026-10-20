FROM mcr.microsoft.com/dotnet/sdk:8.0 AS build
WORKDIR /src
COPY Payesh.SyncServer/Payesh.SyncServer.csproj Payesh.SyncServer/
RUN dotnet restore Payesh.SyncServer/Payesh.SyncServer.csproj
COPY Payesh.SyncServer/ Payesh.SyncServer/
RUN dotnet publish Payesh.SyncServer/Payesh.SyncServer.csproj -c Release -o /out --no-restore

FROM mcr.microsoft.com/dotnet/aspnet:8.0
WORKDIR /app
ENV ASPNETCORE_URLS=http://+:8080
ENV PAYESH_SYNC_DB=/data/payesh-sync.db
RUN mkdir -p /data && chown -R app:app /data
COPY --from=build /out/ ./
USER app
EXPOSE 8080
ENTRYPOINT ["dotnet","Payesh.SyncServer.dll"]
