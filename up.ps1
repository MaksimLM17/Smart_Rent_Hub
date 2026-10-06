<#
.SYNOPSIS
    Convenience wrapper to start local infrastructure from repository root.
#>
$script = Join-Path $PSScriptRoot "infra/scripts/up.ps1"
& $script @args
exit $LASTEXITCODE
