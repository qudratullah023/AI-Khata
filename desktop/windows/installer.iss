#define MyAppName "AI Khata"
#define MyAppVersion "3.2.0"
#define MyAppPublisher "AI Khata"
#define MyAppExeName "AI-Khata.exe"
[Setup]
AppId={{A17F4F73-3C4B-4F43-9E37-2B7D32A11A20}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={autopf}\AI Khata
DefaultGroupName=AI Khata
OutputDir=dist
OutputBaseFilename=AI-Khata-Setup
Compression=lzma
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=lowest
ArchitecturesInstallIn64BitMode=x64
UninstallDisplayIcon={app}\AI-Khata.exe
[Files]
Source: "dist\AI-Khata.exe"; DestDir: "{app}"; Flags: ignoreversion
[Icons]
Name: "{group}\AI Khata"; Filename: "{app}\AI-Khata.exe"
Name: "{autodesktop}\AI Khata"; Filename: "{app}\AI-Khata.exe"; Tasks: desktopicon
[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Additional shortcuts:"
[Run]
Filename: "{app}\AI-Khata.exe"; Description: "Launch AI Khata"; Flags: nowait postinstall skipifsilent
