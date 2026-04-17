# ============================================================
#  AI Training Logger — Auto Setup & Run Script (PowerShell)
#  Run this once from the project root directory:
#    .\setup_and_run.ps1
# ============================================================

$ErrorActionPreference = "Stop"
Write-Host ""
Write-Host "╔══════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║  AI Model Training Logger — Setup & Run              ║" -ForegroundColor Cyan
Write-Host "╚══════════════════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""

# ── Step 1: Detect Java ───────────────────────────────────────────────────────
Write-Host "► Checking for Java..." -ForegroundColor Yellow

$javaPath = $null
$commonJavaPaths = @(
    "C:\Program Files\Java",
    "C:\Program Files\Eclipse Adoptium",
    "C:\Program Files\Microsoft",
    "C:\Program Files\Amazon Corretto",
    "C:\Program Files\BellSoft",
    "C:\Program Files\Zulu",
    "$env:LOCALAPPDATA\Programs\Eclipse Adoptium"
)

foreach ($root in $commonJavaPaths) {
    if (Test-Path $root) {
        $found = Get-ChildItem $root -Recurse -Filter "javac.exe" -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($found) {
            $javaPath = $found.DirectoryName
            break
        }
    }
}

if (-not $javaPath) {
    # Try winget to install JDK 17
    Write-Host "  Java not found. Attempting to install JDK 17 via winget..." -ForegroundColor Magenta
    winget install Microsoft.OpenJDK.17 --silent --accept-package-agreements --accept-source-agreements
    # Try again after install
    $found = Get-ChildItem "C:\Program Files\Microsoft" -Recurse -Filter "javac.exe" -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($found) { $javaPath = $found.DirectoryName }
}

if ($javaPath) {
    $env:JAVA_HOME = (Split-Path $javaPath -Parent)
    $env:PATH = "$javaPath;$env:PATH"
    Write-Host "  ✅ Java found at: $javaPath" -ForegroundColor Green
    & "$javaPath\java.exe" -version
} else {
    Write-Host "  ❌ Java not found! Please install JDK 17 from:" -ForegroundColor Red
    Write-Host "     https://adoptium.net/temurin/releases/?version=17" -ForegroundColor White
    Write-Host "     Then re-run this script." -ForegroundColor White
    exit 1
}

Write-Host ""

# ── Step 2: Detect or Download Maven ─────────────────────────────────────────
Write-Host "► Checking for Maven..." -ForegroundColor Yellow

$mvnExe = $null
$mavenPaths = @(
    "C:\Program Files\Apache Maven",
    "C:\maven",
    "$env:USERPROFILE\maven",
    "$env:LOCALAPPDATA\Programs\Maven"
)

foreach ($root in $mavenPaths) {
    if (Test-Path $root) {
        $found = Get-ChildItem $root -Recurse -Filter "mvn.cmd" -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($found) {
            $mvnExe = $found.FullName
            $env:PATH = "$($found.DirectoryName);$env:PATH"
            break
        }
    }
}

if (-not $mvnExe) {
    Write-Host "  Maven not found. Downloading Maven 3.9.6..." -ForegroundColor Magenta
    $mavenVersion = "3.9.6"
    $mavenUrl = "https://archive.apache.org/dist/maven/maven-3/$mavenVersion/binaries/apache-maven-$mavenVersion-bin.zip"
    $mavenZip = "$env:TEMP\maven.zip"
    $mavenDir = "C:\maven\apache-maven-$mavenVersion"

    Invoke-WebRequest -Uri $mavenUrl -OutFile $mavenZip -UseBasicParsing
    Expand-Archive -Path $mavenZip -DestinationPath "C:\maven" -Force
    Remove-Item $mavenZip

    $mvnExe = "$mavenDir\bin\mvn.cmd"
    $env:PATH = "$mavenDir\bin;$env:PATH"
    Write-Host "  ✅ Maven installed at: $mavenDir" -ForegroundColor Green
}

Write-Host "  ✅ Maven found: $mvnExe" -ForegroundColor Green
& $mvnExe -version

Write-Host ""

# ── Step 3: Build ─────────────────────────────────────────────────────────────
Write-Host "► Building project (mvn clean package)..." -ForegroundColor Yellow
Set-Location $PSScriptRoot
& $mvnExe clean package -q

if ($LASTEXITCODE -ne 0) {
    Write-Host "  ❌ Build FAILED. Check the output above." -ForegroundColor Red
    exit 1
}
Write-Host "  ✅ Build successful!" -ForegroundColor Green

Write-Host ""

# ── Step 4: Run ───────────────────────────────────────────────────────────────
Write-Host "► Running AI Training Logger Demo..." -ForegroundColor Yellow
Write-Host "═" * 60 -ForegroundColor Cyan

$jar = Get-ChildItem "target\AITrainingLogger-*.jar" | Where-Object {$_.Name -notmatch "original"} | Select-Object -First 1
& "$javaPath\java.exe" -jar $jar.FullName

Write-Host ""
Write-Host "✅ Done! Check these output files:" -ForegroundColor Green
Write-Host "   📄 Database  : aitraining_log.db" -ForegroundColor White
Write-Host "   📊 Reports   : reports\" -ForegroundColor White
Write-Host "   📝 App Logs  : logs\aitraining.log" -ForegroundColor White
