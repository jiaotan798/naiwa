param([Parameter(ValueFromRemainingArguments = $true)][string[]]$GradleArgs = @('build'))
$ErrorActionPreference = 'Stop'
# 使用调用者已安装的 JDK，不修改原工作区或游戏实例；结束后恢复调用目录。
Push-Location -LiteralPath (Join-Path $PSScriptRoot '../mod')
try {
    & '.\gradlew.bat' @GradleArgs
    $naiwaExitCode = $LASTEXITCODE
} finally {Pop-Location}
exit $naiwaExitCode
