# ЗАПУСК ТОЛЬКО МОНИТОРИНГА

. .\lib\functions.ps1

Write-Section "ЗАПУСК МОНИТОРИНГА"

# Проверка Docker
if (-not (Test-Docker)) {
    Write-Error "Docker не установлен!"
    exit 1
}

# Останавливаем старые контейнеры мониторинга
Write-Step "Останавливаем старые контейнеры мониторинга..."
docker-compose stop prometheus grafana alertmanager loki promtail jaeger 2>&1 | Out-Null
docker-compose rm -f prometheus grafana alertmanager loki promtail jaeger 2>&1 | Out-Null
Write-Success "Старые контейнеры удалены"

# Запускаем мониторинг
Write-Step "Запускаем мониторинг..."
docker-compose up -d prometheus grafana alertmanager loki promtail jaeger
Write-Success "Мониторинг запущен"

Start-Sleep -Seconds 10

# Проверяем мониторинг
Check-Monitoring

# Показываем результат
Write-Section "МОНИТОРИНГ ЗАПУЩЕН!"
Show-Containers

Write-Color "`n  Доступные сервисы:" $Script:Colors.Info
Write-Color "    Prometheus:  http://localhost:9090" $Script:Colors.Info
Write-Color "    Grafana:     http://localhost:3000 (admin/admin)" $Script:Colors.Info
Write-Color "    Alertmanager: http://localhost:9093" $Script:Colors.Info
Write-Color "    Loki:        http://localhost:3100" $Script:Colors.Info
Write-Color "    Jaeger:      http://localhost:16686" $Script:Colors.Info

Write-Color "`n  Grafana дашборды:" $Script:Colors.Info
Write-Color "    Spring Boot: http://localhost:3000/d/spring-boot-dashboard" $Script:Colors.Info
Write-Color "    (импортируйте готовые дашборды через UI)" $Script:Colors.Info