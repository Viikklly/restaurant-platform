# Быстрый старт для разработки

. .\run-common.ps1

Write-SectionHeader "БЫСТРЫЙ СТАРТ"

docker-compose down -v 2>$null
docker-compose down --remove-orphans 2>$null

docker-compose -f docker-compose.infra.yml up -d

Start-Sleep -Seconds 15

Write-SectionHeader "ГОТОВО!"
Show-ContainerStatus