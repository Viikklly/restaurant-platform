# Статус контейнеров

param([switch]$Watch)

. .\run-common.ps1

if ($Watch) {
    while ($true) {
        Clear-Host
        Show-ContainerStatus
        Start-Sleep -Seconds 5
    }
} else {
    Write-SectionHeader "СТАТУС КОНТЕЙНЕРОВ"
    Show-ContainerStatus
}