# ПОЛНЫЙ ЗАПУСК ВСЕХ СЕРВИСОВ

. .\lib\functions.ps1

Write-Section "ПОЛНЫЙ ЗАПУСК"

# Проверка Docker
if (-not (Test-Docker)) {
    Write-Error "Docker не установлен!"
    exit 1
}


Write-Step "Проверка и удаление ZooKeeper..."
$zookeeperExists = docker ps -a | Select-String "restaurant-zookeeper"
if ($zookeeperExists) {
    Write-Warning "Найден старый контейнер ZooKeeper. Удаляем..."
    docker rm -f restaurant-zookeeper 2>$null
    Write-Success "ZooKeeper удалён"
} else {
    Write-Success "ZooKeeper не найден"
}

# Останавливаем всё
Write-Step "Останавливаем старые контейнеры..."
docker-compose down 2>&1 | Out-Null

# Запускаем БД и брокеры
Write-Step "Запускаем БД и брокеры..."
docker-compose up -d postgres-auth postgres-order postgres-kitchen postgres-payment redis kafka
Write-Success "БД и брокеры запущены"

Start-Sleep -Seconds 15

# Запускаем мониторинг (ОДИН Loki!)
Write-Step "Запускаем мониторинг (Prometheus, Grafana, Loki, Promtail, Jaeger, Alertmanager)..."
docker-compose up -d prometheus grafana alertmanager loki promtail jaeger
Write-Success "Мониторинг запущен"

Start-Sleep -Seconds 15

# Запускаем Discovery
Write-Step "Запускаем Discovery Service..."
docker-compose up -d discovery-service
Write-Success "Discovery Service запущен"

Start-Sleep -Seconds 15

# Запускаем Config
Write-Step "Запускаем Config Service..."
docker-compose up -d config-service
Write-Success "Config Service запущен"

Start-Sleep -Seconds 30

# Запускаем микросервисы
Write-Step "Запускаем микросервисы..."
docker-compose up -d gateway-service auth-service order-service kitchen-service payment-service notification-service
Write-Success "Микросервисы запущены"

Start-Sleep -Seconds 20

# Проверяем мониторинг
Check-Monitoring

# Показываем результат
Write-Section "ГОТОВО!"
Show-Containers
Show-Urls