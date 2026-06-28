# run-stop.ps1 - Остановка

param([switch]$Clean, [switch]$Infra)

. .\run-common.ps1

Write-SectionHeader "ОСТАНОВКА СЕРВИСОВ"

if ($Infra) {
    Write-Step "Останавливаем инфраструктуру..."
    if ($Clean) {
        docker-compose -f docker-compose.infra.yml down -v
    } else {
        docker-compose -f docker-compose.infra.yml down
    }
} else {
    Write-Step "Останавливаем все сервисы..."
    if ($Clean) {
        docker-compose down -v
    } else {
        docker-compose down
    }
}

Write-Success "Сервисы остановлены"