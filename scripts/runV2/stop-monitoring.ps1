# stop-monitoring.ps1
# ОСТАНОВКА МОНИТОРИНГА

param(
    [switch]$Clean    # Удалить тома
)

. .\lib\functions.ps1

Write-Section "ОСТАНОВКА МОНИТОРИНГА"

# Проверка Docker
if (-not (Test-Docker)) {
    Write-Error "Docker не установлен!"
    exit 1
}

# Определяем compose файл
if (Test-Path "docker-compose.infra.yml") {
    $composeFile = "docker-compose.infra.yml"
} elseif (Test-Path "docker-compose.yml") {
    $composeFile = "docker-compose.yml"
} else {
    Write-Error "Compose файл не найден!"
    exit 1
}

if ($Clean) {
    Write-Step "Останавливаем мониторинг и удаляем тома..."
    docker-compose -f $composeFile down -v
    Write-Success "Мониторинг остановлен, тома удалены"
} else {
    Write-Step "Останавливаем мониторинг..."
    docker-compose -f $composeFile down
    Write-Success "Мониторинг остановлен"
}

# Показываем оставшиеся контейнеры
Write-Step "Оставшиеся контейнеры:"
docker ps --format "table {{.Names}}\t{{.Status}}" --filter "name=restaurant-"

Write-Color "`n  Для повторного запуска:" $Script:Colors.Info
Write-Color "    .\start-monitoring.ps1" $Script:Colors.Info