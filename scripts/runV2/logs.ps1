# logs.ps1
# ПРОСМОТР ЛОГОВ

param(
    [string]$Service,          # Имя сервиса
    [switch]$All,              # Все сервисы
    [switch]$Follow,           # Следить за логами
    [int]$Lines = 50,          # Количество строк
    [string]$Filter            # Фильтр по тексту
)

. .\lib\functions.ps1

if (-not $Service -and -not $All) {
    Write-Error "Укажите сервис или используйте -All"
    Write-Color "`nПримеры:" $Script:Colors.Info
    Write-Color "  .\logs.ps1 auth-service" $Script:Colors.Info
    Write-Color "  .\logs.ps1 -All -Follow" $Script:Colors.Info
    Write-Color "  .\logs.ps1 gateway-service -Filter 'ERROR'" $Script:Colors.Info
    exit 1
}

if ($Service) {
    $cmd = "docker-compose logs --tail=$Lines"
    if ($Follow) { $cmd += " -f" }
    $cmd += " $Service"

    if ($Filter) {
        Invoke-Expression $cmd | Select-String -Pattern $Filter
    } else {
        Invoke-Expression $cmd
    }
} else {
    $cmd = "docker-compose logs --tail=$Lines"
    if ($Follow) { $cmd += " -f" }

    if ($Filter) {
        Invoke-Expression $cmd | Select-String -Pattern $Filter
    } else {
        Invoke-Expression $cmd
    }
}