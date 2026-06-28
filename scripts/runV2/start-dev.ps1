# ЗАПУСК ДЛЯ РАЗРАБОТКИ (только инфраструктура)

. .\lib\functions.ps1

Write-Section "ЗАПУСК ДЛЯ РАЗРАБОТКИ"

# Проверка Docker
if (-not (Test-Docker)) {
    Write-Error "Docker не установлен!"
    exit 1
}

# 1. Полная очистка
Write-Step "Полная очистка..."
docker-compose down -v 2>&1 | Out-Null
Write-Success "Контейнеры и тома удалены"

Start-Sleep -Seconds 5

# 2. Удаляем orphans
Write-Step "Удаляем orphan контейнеры..."
docker-compose down --remove-orphans 2>&1 | Out-Null
Write-Success "Orphan контейнеры удалены"

Start-Sleep -Seconds 5

# 3. Показываем статус
Write-Step "Текущий статус:"
docker ps -a

Start-Sleep -Seconds 10

# 4. Запускаем инфраструктуру
Write-Step "Запускаем инфраструктуру..."
docker-compose -f docker-compose.infra.yml up -d
Write-Success "Инфраструктура запущена"

Start-Sleep -Seconds 15

# 5. Показываем результат
Write-Section "ГОТОВО!"
Show-Containers
Show-Urls
