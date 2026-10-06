param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$Root = Split-Path $PSScriptRoot -Parent
Set-Location $Root
if ($JavaHome) {
    $env:JAVA_HOME = $JavaHome
    $env:PATH = (Join-Path $JavaHome 'bin') + ';' + $env:PATH
}
if (-not (Get-Command javac -ErrorAction SilentlyContinue)) { throw 'A build JDK (17/21) is required.' }
New-Item -ItemType Directory -Path 'verification' -Force | Out-Null
if (Get-Command py -ErrorAction SilentlyContinue) {
    & py -3 scripts/gradle_bootstrap.py --no-daemon --console=plain --stacktrace clean check reobfJar stageCloudArtifact 2>&1 | Tee-Object -FilePath verification/full-build-local.log
} elseif (Get-Command python -ErrorAction SilentlyContinue) {
    & python scripts/gradle_bootstrap.py --no-daemon --console=plain --stacktrace clean check reobfJar stageCloudArtifact 2>&1 | Tee-Object -FilePath verification/full-build-local.log
} else { throw 'Python 3.9+ is required for the checksum-verified bootstrap. Alternatively use Gradle 8.8 directly.' }
if ($LASTEXITCODE -ne 0) { throw 'Build failed. Do not install a dev JAR.' }
Write-Host 'Artifact directory: build/cloud-artifact. Game startup has not been tested by this command.'
