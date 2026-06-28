# run-infra.ps1 - Запуск инфраструктуры

param([switch]$Clean)

. .\run-common.ps1

Write-SectionHeader "ЗАПУСК ИНФРАСТРУКТУРЫ"

if (-not (Test-DockerInstalled)) {
    Write-Error "Docker не установлен!"
    exit 1
}

Write-Step "Останавливаем контейнеры..."
docker-compose -f docker-compose.infra.yml down 2>&1 | Out-Null

if ($Clean) {
    Write-Step "Удаляем тома..."
    docker-compose -f docker-compose.infra.yml down -v 2>&1 | Out-Null
}

Write-Step "Запускаем инфраструктуру..."
docker-compose -f docker-compose.infra.yml up -d
Write-Success "Инфраструктура запущена"

Start-Sleep -Seconds 15

Write-SectionHeader "ГОТОВО!"
Show-ContainerStatus
Show-MonitoringStatus
Show-AllUrls