#define MyAppName "پایش"
#define MyAppVersion "0.1.0"
#ifndef PublishDir
#define PublishDir "..\..\artifacts\publish"
#endif
[Setup]
AppId={{C2A7E2F4-6F9B-47A1-88C3-1F1B51B60D21}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher=Payesh
DefaultDirName={autopf}\Payesh
DefaultGroupName={#MyAppName}
OutputDir=..\..\artifacts\installer
OutputBaseFilename=Payesh-Setup-x64
SetupIconFile=..\Payesh.Desktop\payesh.ico
UninstallDisplayIcon={app}\Payesh.exe
ArchitecturesInstallIn64BitMode=x64
PrivilegesRequired=lowest
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
DisableProgramGroupPage=yes
[Tasks]
Name: "desktopicon"; Description: "ساخت میانبر روی دسکتاپ"; GroupDescription: "میانبرها:"; Flags: checkedonce
[Files]
Source: "{#PublishDir}\Payesh.exe"; DestDir: "{app}"; Flags: ignoreversion
[Icons]
Name: "{autoprograms}\{#MyAppName}"; Filename: "{app}\Payesh.exe"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\Payesh.exe"; Tasks: desktopicon
[Run]
Filename: "{app}\Payesh.exe"; Description: "اجرای پایش"; Flags: postinstall nowait skipifsilent
