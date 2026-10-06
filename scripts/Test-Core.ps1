$ErrorActionPreference='Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
New-Item -ItemType Directory -Path 'build\offline-core','verification' -Force | Out-Null
$Sources=@((Get-ChildItem -LiteralPath 'src\main\java\dev\chronolink\core' -Filter '*.java' -File).FullName)
$Sources+=Join-Path (Get-Location) 'src\test\java\dev\chronolink\core\CoreTests.java'
& javac --release 8 -encoding UTF-8 -d 'build\offline-core' $Sources
if($LASTEXITCODE -ne 0){throw 'Core compilation failed'}
& java -ea -cp 'build\offline-core' dev.chronolink.core.CoreTests | Tee-Object -FilePath 'verification\core-tests.txt'
if($LASTEXITCODE -ne 0){throw 'Core tests failed'}
