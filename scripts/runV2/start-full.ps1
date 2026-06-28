# ПОЛНЫЙ ЗАПУСК ВСЕХ СЕРВИСОВ

. .\lib\functions.ps1

Write-Section "ПОЛНЫЙ ЗАПУСК"

# Проверка Docker
if (-not (Test-Docker)) {
    Write-Error "Docker не установлен!"
    exit 1
}

# 1. Останавливаем всё
Write-Step "Останавливаем старые контейнеры..."
docker-compose down 2>&1 | Out-Null

# 2. Запускаем БД и брокеры
Write-Step "Запускаем БД и брокеры..."
docker-compose up -d postgres-auth postgres-order postgres-kitchen postgres-payment redis kafka
Write-Success "БД и брокеры запущены"

Start-Sleep -Seconds 15

# 3. Запускаем мониторинг
Write-Step "Запускаем мониторинг..."
docker-compose up -d prometheus grafana jaeger
Write-Success "Мониторинг запущен"

Start-Sleep -Seconds 15

# 4. Запускаем Discovery
Write-Step "Запускаем Discovery Service..."
docker-compose up -d discovery-service
Write-Success "Discovery Service запущен"

Start-Sleep -Seconds 15

# 5. Запускаем Config
Write-Step "Запускаем Config Service..."
docker-compose up -d config-service
Write-Success "Config Service запущен"

Start-Sleep -Seconds 30

# 6. Запускаем микросервисы
Write-Step "Запускаем микросервисы..."
docker-compose up -d gateway-service auth-service order-service kitchen-service payment-service notification-service
Write-Success "Микросервисы запущены"

Start-Sleep -Seconds 20

# 7. Проверяем Eureka
Check-Eureka

# 8. Показываем результат
Write-Section "ГОТОВО!"
Show-Containers
Show-Urls
