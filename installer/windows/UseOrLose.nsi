; Use or Lose Windows installer.
; Compile-time VERSION, NUMERIC_VERSION, SOURCE_DIR, and OUTPUT_DIR definitions
; are supplied by installer/package.py and the distribution workflow.

Unicode true
RequestExecutionLevel admin
SetCompressor /SOLID lzma

!include "MUI2.nsh"
!include "LogicLib.nsh"

!ifndef VERSION
  !error "VERSION is required (for example /DVERSION=1.2.3)"
!endif
!ifndef NUMERIC_VERSION
  !error "NUMERIC_VERSION is required (for example /DNUMERIC_VERSION=1.2.3.0)"
!endif
!ifndef SOURCE_DIR
  !error "SOURCE_DIR is required and must point to the verified PyInstaller bundle"
!endif
!ifndef OUTPUT_DIR
  !error "OUTPUT_DIR is required"
!endif

; These checks complement package.py's pyproject-driven full validation and
; make a direct NSIS invocation fail clearly for the core applications.
!if ! /FileExists "${SOURCE_DIR}\useorlose.exe"
  !error "Required useorlose CLI is missing from SOURCE_DIR"
!endif
!if ! /FileExists "${SOURCE_DIR}\useorlose-mcp.exe"
  !error "Required Use or Lose MCP executable is missing from SOURCE_DIR"
!endif
!if ! /FileExists "${SOURCE_DIR}\UseOrLoseCalendar.exe"
  !error "Required Use or Lose Calendar executable is missing from SOURCE_DIR"
!endif
!if ! /FileExists "${SOURCE_DIR}\UseOrLoseAnalytics.exe"
  !error "Required Use or Lose Analytics executable is missing from SOURCE_DIR"
!endif

!define PRODUCT_NAME "Use or Lose"
!define PRODUCT_PUBLISHER "Joshua Guthrie"
!define PRODUCT_URL "https://www.westmouthbay.com/useorlose-application/"
!define PRODUCT_REG_KEY "Software\Microsoft\Windows\CurrentVersion\Uninstall\Use or Lose"
!define MUI_ABORTWARNING
!define MUI_ICON "..\..\assets\useorlose-icon.ico"
!define MUI_UNICON "..\..\assets\useorlose-icon.ico"

Name "${PRODUCT_NAME} ${VERSION}"
OutFile "${OUTPUT_DIR}\UseOrLose-Setup-${VERSION}-Windows-x64.exe"
InstallDir "$PROGRAMFILES64\UseOrLose"
InstallDirRegKey HKLM "${PRODUCT_REG_KEY}" "InstallLocation"
BrandingText "Use or Lose"

VIProductVersion "${NUMERIC_VERSION}"
VIAddVersionKey /LANG=1033 "ProductName" "${PRODUCT_NAME}"
VIAddVersionKey /LANG=1033 "ProductVersion" "${VERSION}"
VIAddVersionKey /LANG=1033 "FileVersion" "${VERSION}"
VIAddVersionKey /LANG=1033 "CompanyName" "${PRODUCT_PUBLISHER}"
VIAddVersionKey /LANG=1033 "Comments" "${PRODUCT_URL}"
VIAddVersionKey /LANG=1033 "FileDescription" "Use or Lose Setup"
VIAddVersionKey /LANG=1033 "LegalCopyright" "MIT License"

!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_COMPONENTS
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH

!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_UNPAGE_FINISH
!insertmacro MUI_LANGUAGE "English"

Section "Use or Lose application files" SEC_CORE
  SectionIn RO
  SetShellVarContext all
  SetRegView 64

  ; Writable records/configuration live in the user's normal application-data
  ; directory. Removing the Program Files tree here clears obsolete binaries
  ; during upgrades without touching user data.
  RMDir /r "$INSTDIR"
  SetOutPath "$INSTDIR"
  File /r "${SOURCE_DIR}\*"

  WriteUninstaller "$INSTDIR\Uninstall.exe"

  ; The stable registry key is reused across releases, so Windows Installed
  ; Apps treats upgrades as the same product. QuietUninstallString enables
  ; management tools to perform the same unattended uninstall tested in CI.
  WriteRegStr HKLM "${PRODUCT_REG_KEY}" "DisplayName" "${PRODUCT_NAME}"
  WriteRegStr HKLM "${PRODUCT_REG_KEY}" "DisplayVersion" "${VERSION}"
  WriteRegStr HKLM "${PRODUCT_REG_KEY}" "Publisher" "${PRODUCT_PUBLISHER}"
  WriteRegStr HKLM "${PRODUCT_REG_KEY}" "URLInfoAbout" "${PRODUCT_URL}"
  WriteRegStr HKLM "${PRODUCT_REG_KEY}" "HelpLink" "${PRODUCT_URL}"
  WriteRegStr HKLM "${PRODUCT_REG_KEY}" "InstallLocation" "$INSTDIR"
  WriteRegStr HKLM "${PRODUCT_REG_KEY}" "DisplayIcon" "$INSTDIR\UseOrLoseCalendar.exe"
  WriteRegStr HKLM "${PRODUCT_REG_KEY}" "UninstallString" '"$INSTDIR\Uninstall.exe"'
  WriteRegStr HKLM "${PRODUCT_REG_KEY}" "QuietUninstallString" '"$INSTDIR\Uninstall.exe" /S'
  WriteRegDWORD HKLM "${PRODUCT_REG_KEY}" "NoModify" 1
  WriteRegDWORD HKLM "${PRODUCT_REG_KEY}" "NoRepair" 1

  RMDir /r "$SMPROGRAMS\Use or Lose"
  CreateDirectory "$SMPROGRAMS\Use or Lose"
  CreateShortcut "$SMPROGRAMS\Use or Lose\Use or Lose Calendar.lnk" \
    "$INSTDIR\UseOrLoseCalendar.exe" "" \
    "$INSTDIR\UseOrLoseCalendar.exe"
  CreateShortcut "$SMPROGRAMS\Use or Lose\Use or Lose Analytics.lnk" \
    "$INSTDIR\UseOrLoseAnalytics.exe" "" \
    "$INSTDIR\UseOrLoseAnalytics.exe"
  CreateShortcut "$SMPROGRAMS\Use or Lose\Uninstall Use or Lose.lnk" "$INSTDIR\Uninstall.exe"
SectionEnd

Section /o "Desktop shortcut" SEC_DESKTOP
  SetShellVarContext all
  ; Optional in the wizard and omitted by default during /S installation.
  CreateShortcut "$DESKTOP\Use or Lose Calendar.lnk" \
    "$INSTDIR\UseOrLoseCalendar.exe" "" \
    "$INSTDIR\UseOrLoseCalendar.exe"
SectionEnd

LangString DESC_SEC_CORE ${LANG_ENGLISH} "Install Use or Lose and all packaged companion applications."
LangString DESC_SEC_DESKTOP ${LANG_ENGLISH} "Create a public Desktop shortcut for Use or Lose Calendar."
!insertmacro MUI_FUNCTION_DESCRIPTION_BEGIN
  !insertmacro MUI_DESCRIPTION_TEXT ${SEC_CORE} $(DESC_SEC_CORE)
  !insertmacro MUI_DESCRIPTION_TEXT ${SEC_DESKTOP} $(DESC_SEC_DESKTOP)
!insertmacro MUI_FUNCTION_DESCRIPTION_END

Section "Uninstall"
  SetShellVarContext all
  SetRegView 64
  Delete "$DESKTOP\Use or Lose Calendar.lnk"
  RMDir /r "$SMPROGRAMS\Use or Lose"
  DeleteRegKey HKLM "${PRODUCT_REG_KEY}"

  ; User data is deliberately outside $INSTDIR and is never referenced here.
  ; /S is handled natively by NSIS, so this same section supports unattended
  ; uninstall without an auxiliary runtime or PowerShell.
  RMDir /r "$INSTDIR"
SectionEnd
