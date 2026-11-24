#define MyAppName "AI Khata Desktop"
#define MyAppVersion "1.0.0"
#define MyAppPublisher "AI Khata"
#define MyAppExeName "AI-Khata.exe"

[Setup]
AppId={{A17F4F73-3C4B-4F43-9E37-2B7D32A11A20}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} {#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={autopf}\AI Khata Desktop
DefaultGroupName=AI Khata Desktop
OutputDir=dist
OutputBaseFilename=AI-Khata-Setup
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=lowest
ArchitecturesAllowed=x64
ArchitecturesInstallIn64BitMode=x64
UninstallDisplayIcon={app}\AI-Khata.exe
SetupLogging=yes

[Files]
Source: "dist\AI-Khata.exe"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{group}\AI Khata Desktop"; Filename: "{app}\AI-Khata.exe"
Name: "{autodesktop}\AI Khata Desktop"; Filename: "{app}\AI-Khata.exe"; Tasks: desktopicon

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Additional shortcuts:"

[Run]
Filename: "{app}\AI-Khata.exe"; Description: "Launch AI Khata Desktop"; Flags: nowait postinstall skipifsilent
