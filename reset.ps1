<#
.SYNOPSIS
    Convenience wrapper to reset local infrastructure from repository root.
#>
$script = Join-Path $PSScriptRoot "infra/scripts/reset.ps1"
& $script @args
exit $LASTEXITCODE
