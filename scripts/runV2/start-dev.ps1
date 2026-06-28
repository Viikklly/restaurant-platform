# ЗАПУСК ДЛЯ РАЗРАБОТКИ (только инфраструктура)

. .\lib\functions.ps1

Write-Section "ЗАПУСК ДЛЯ РАЗРАБОТКИ"

if (-not (Test-Docker)) {
    Write-Error "Docker не установлен!"
    exit 1
}

# Определяем путь к compose файлам
$composeInfra = "..\..\docker-compose.infra.yml"

# Проверяем наличие файла
if (-not (Test-Path $composeInfra)) {
    Write-Error "Файл $composeInfra не найден!"
    Write-Color "`n  Проверьте, что файл находится в корне проекта" $Script:Colors.Warning
    Write-Color "  Ожидаемый путь: ..\..\docker-compose.infra.yml" $Script:Colors.Info
    exit 1
}

# Очистка
Write-Step "Очистка старых контейнеров..."
docker-compose -f $composeInfra down -v 2>&1 | Out-Null
docker-compose -f $composeInfra down --remove-orphans 2>&1 | Out-Null
Write-Success "Очистка выполнена"

# Запуск
Write-Step "Запуск инфраструктуры и мониторинга..."
docker-compose -f $composeInfra up -d
Write-Success "Запуск выполнен"

# Пауза для инициализации
Write-Step "Ожидание инициализации сервисов..."
Start-Sleep -Seconds 15

# Результат
Write-Section "ГОТОВО!"
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
Show-Urls

Write-Success "`n Запуск микросервисов через IDE."
Write-Color "    1. Discovery Service   → http://localhost:8761" $Script:Colors.Info
Write-Color "    2. Config Service      → http://localhost:8888" $Script:Colors.Info
Write-Color "    3. Auth Service        → http://localhost:8081" $Script:Colors.Info
Write-Color "    4. Order Service       → http://localhost:8082" $Script:Colors.Info
Write-Color "    5. Kitchen Service     → http://localhost:8083" $Script:Colors.Info
Write-Color "    6. Payment Service     → http://localhost:8084" $Script:Colors.Info
Write-Color "    7. Notification Service → http://localhost:8086" $Script:Colors.Info
Write-Color "    8. Gateway Service     → http://localhost:8090" $Script:Colors.Info