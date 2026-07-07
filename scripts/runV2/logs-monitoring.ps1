# logs-monitoring.ps1
# ПРОСМОТР ЛОГОВ МОНИТОРИНГА

param(
    [switch]$Follow,   # Следить за логами
    [string]$Service,  # Конкретный сервис мониторинга
    [int]$Lines = 30   # Количество строк
)

. .\lib\functions.ps1

Write-Section "ЛОГИ МОНИТОРИНГА"

$monitoringServices = @(
    "loki",
    "promtail",
    "grafana",
    "prometheus",
    "alertmanager",
    "jaeger"
)

# Определяем compose файл
if (Test-Path "docker-compose.infra.yml") {
    $composeFile = "docker-compose.infra.yml"
    Write-Step "Используем compose-файл: $composeFile"
} else {
    $composeFile = "docker-compose.yml"
    Write-Step "Используем compose-файл: $composeFile"
}

if ($Service) {
    if ($monitoringServices -notcontains $Service) {
        Write-Error "Сервис '$Service' не найден в списке мониторинга"
        Write-Color "`n  Доступные сервисы мониторинга:" $Script:Colors.Info
        foreach ($svc in $monitoringServices) {
            Write-Color "    - $svc" $Script:Colors.Info
        }
        exit 1
    }

    Write-Step "Логи сервиса: $Service"
    if ($Follow) {
        docker-compose -f $composeFile logs -f $Service
    } else {
        docker-compose -f $composeFile logs --tail=$Lines $Service
    }
} else {
    Write-Step "Логи всех сервисов мониторинга (последние $Lines строк)"
    if ($Follow) {
        docker-compose -f $composeFile logs -f
    } else {
        docker-compose -f $composeFile logs --tail=$Lines
    }
}