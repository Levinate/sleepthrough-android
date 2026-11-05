param([string]$SdkRoot=$env:ANDROID_HOME,[string]$JavaHome=$env:JAVA_HOME)
$ErrorActionPreference='Stop'
Set-Location -LiteralPath $PSScriptRoot
if(-not $SdkRoot -or -not $JavaHome){throw 'Set ANDROID_HOME to your Android SDK and JAVA_HOME to JDK 21.'}
$env:ANDROID_HOME=$SdkRoot
$env:JAVA_HOME=$JavaHome
$env:PATH="$JavaHome/bin;$env:PATH"
$pnpmCommand=Get-Command pnpm -ErrorAction SilentlyContinue
if($pnpmCommand){& $pnpmCommand.Source install --frozen-lockfile;if($LASTEXITCODE){throw 'Dependencies failed'}}
elseif(-not(Test-Path -LiteralPath 'node_modules')){throw 'Install pnpm and run pnpm install --frozen-lockfile first.'}
node --test test/*.test.mjs
if($LASTEXITCODE){throw 'Tests failed'}
node build.mjs
if($LASTEXITCODE){throw 'Web bundle failed'}
node node_modules/@capacitor/cli/bin/capacitor sync android
if($LASTEXITCODE){throw 'Capacitor sync failed'}
$escapedSdk=$SdkRoot.Replace('\','/').Replace(':','\:')
Set-Content -LiteralPath 'android/local.properties' -Value "sdk.dir=$escapedSdk" -Encoding ascii
Push-Location android
try{./gradlew.bat assembleDebug lintDebug --console=plain;if($LASTEXITCODE){throw 'Android build failed'}}finally{Pop-Location}
Get-FileHash -LiteralPath 'android/app/build/outputs/apk/debug/app-debug.apk' -Algorithm SHA256
