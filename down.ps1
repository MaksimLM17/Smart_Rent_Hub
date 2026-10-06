<#
.SYNOPSIS
    Convenience wrapper to stop local infrastructure from repository root.
#>
$script = Join-Path $PSScriptRoot "infra/scripts/down.ps1"
& $script @args
exit $LASTEXITCODE
