# start-monitoring.ps1
# ЗАПУСК ТОЛЬКО МОНИТОРИНГА (Loki, Promtail, Grafana, Prometheus, Jaeger, Alertmanager)

. .\lib\functions.ps1

Write-Section "ЗАПУСК МОНИТОРИНГА"

# Проверка Docker
if (-not (Test-Docker)) {
    Write-Error "Docker не установлен!"
    exit 1
}

# Проверяем наличие файла docker-compose.infra.yml
if (-not (Test-Path "docker-compose.infra.yml")) {
    Write-Warning "Файл docker-compose.infra.yml не найден, ищем docker-compose.yml..."
    if (Test-Path "docker-compose.yml") {
        $composeFile = "docker-compose.yml"
        Write-Success "Используем docker-compose.yml"
    } else {
        Write-Error "Compose файл не найден!"
        exit 1
    }
} else {
    $composeFile = "docker-compose.infra.yml"
    Write-Success "Используем $composeFile"
}

# Останавливаем старые контейнеры мониторинга
Write-Step "Останавливаем старые контейнеры мониторинга..."
docker-compose -f $composeFile stop loki promtail grafana prometheus alertmanager jaeger 2>&1 | Out-Null
docker-compose -f $composeFile rm -f loki promtail grafana prometheus alertmanager jaeger 2>&1 | Out-Null
Write-Success "Старые контейнеры мониторинга удалены"

# Запускаем мониторинг (один Loki)
Write-Step "Запускаем мониторинг..."
docker-compose -f $composeFile up -d loki promtail grafana prometheus alertmanager jaeger

if ($LASTEXITCODE -eq 0) {
    Write-Success "Мониторинг запущен!"
} else {
    Write-Error "Ошибка при запуске мониторинга!"
    exit 1
}

Start-Sleep -Seconds 15

# Проверяем мониторинг
Write-Section "ПРОВЕРКА МОНИТОРИНГА"

# Проверка контейнеров
Write-Step "Статус контейнеров мониторинга:"
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" --filter "name=restaurant-loki" --filter "name=restaurant-promtail" --filter "name=restaurant-grafana" --filter "name=restaurant-prometheus" --filter "name=restaurant-jaeger" --filter "name=restaurant-alertmanager"

# Проверка эндпоинтов
Write-Step "Проверка доступности эндпоинтов:"
$endpoints = @(
    @{Name="Loki"; Url="http://localhost:3100/ready"},
    @{Name="Promtail"; Url="http://localhost:9080/ready"},
    @{Name="Prometheus"; Url="http://localhost:9090/-/healthy"},
    @{Name="Grafana"; Url="http://localhost:3000/api/health"},
    @{Name="Alertmanager"; Url="http://localhost:9093/-/healthy"},
    @{Name="Jaeger"; Url="http://localhost:16686/api/services"}
)

$allOk = $true
foreach ($ep in $endpoints) {
    try {
        $response = Invoke-WebRequest -Uri $ep.Url -UseBasicParsing -TimeoutSec 5 -ErrorAction Stop
        if ($response.StatusCode -eq 200 -or $response.StatusCode -eq 204) {
            Write-Success "$($ep.Name) - OK"
        } else {
            Write-Warning "$($ep.Name) - статус $($response.StatusCode)"
            $allOk = $false
        }
    } catch {
        Write-Error "$($ep.Name) - НЕ ДОСТУПЕН"
        $allOk = $false
    }
}

# Показываем результат
Write-Section "МОНИТОРИНГ ЗАПУЩЕН!"

Write-Color "`n  Доступные сервисы:" $Script:Colors.Info
Write-Color "    Prometheus:   http://localhost:9090" $Script:Colors.Info
Write-Color "    Grafana:      http://localhost:3000 (admin/admin)" $Script:Colors.Info
Write-Color "    Alertmanager: http://localhost:9093" $Script:Colors.Info
Write-Color "    Loki:         http://localhost:3100" $Script:Colors.Info
Write-Color "    Jaeger:       http://localhost:16686" $Script:Colors.Info

Write-Color "`n  Просмотр логов в Grafana:" $Script:Colors.Info
Write-Color "    1. http://localhost:3000 (admin/admin)" $Script:Colors.Info
Write-Color "    2. Добавьте источник данных: Loki → http://loki:3100" $Script:Colors.Info
Write-Color "    3. Запрос: {service=~\"auth-service|order-service|gateway-service\"}" $Script:Colors.Info

Write-Color "`n  Просмотр логов через командную строку:" $Script:Colors.Info
Write-Color "    docker logs restaurant-loki -f" $Script:Colors.Info
Write-Color "    docker logs restaurant-promtail -f" $Script:Colors.Info
Write-Color "    docker logs restaurant-grafana -f" $Script:Colors.Info

Write-Color "`n  Полезные команды:" $Script:Colors.Info
Write-Color "    .\check-monitoring.ps1    - проверить состояние мониторинга" $Script:Colors.Info
Write-Color "    .\stop-monitoring.ps1     - остановить мониторинг" $Script:Colors.Info
Write-Color "    .\logs-monitoring.ps1     - посмотреть логи мониторинга" $Script:Colors.Info

if ($allOk) {
    Write-Success "`n ВСЕ КОМПОНЕНТЫ МОНИТОРИНГА РАБОТАЮТ!"
} else {
    Write-Warning "`n НЕКОТОРЫЕ КОМПОНЕНТЫ НЕ РАБОТАЮТ"
    Write-Color "     Проверьте логи: docker logs <container_name>" $Script:Colors.Info
    Write-Color "     Или перезапустите: .\start-monitoring.ps1" $Script:Colors.Info
}

Write-Color "`n"