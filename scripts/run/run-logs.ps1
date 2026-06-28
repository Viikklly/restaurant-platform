# Просмотр логов

param(
    [string]$Service,
    [switch]$All,
    [switch]$Follow,
    [int]$Lines = 50
)

. .\run-common.ps1

if ($Service) {
    docker-compose logs --tail=$Lines $($Follow ? "-f" : "") $Service
} elseif ($All) {
    docker-compose logs --tail=$Lines $($Follow ? "-f" : "")
} else {
    Write-Error "Укажите сервис или используйте -All"
    Write-Host "Пример: .\run-logs.ps1 auth-service"
    Write-Host "Пример: .\run-logs.ps1 -All -Follow"
}