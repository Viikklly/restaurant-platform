# ОСТАНОВКА СЕРВИСОВ

param(
    [switch]$Clean,    # Удалить тома
    [switch]$Infra     # Только инфраструктура
)

. .\lib\functions.ps1

Write-Section "ОСТАНОВКА"

if ($Infra) {
    Write-Step "Останавливаем инфраструктуру..."
    if ($Clean) {
        docker-compose -f docker-compose.infra.yml down -v
        Write-Success "Инфраструктура остановлена, тома удалены"
    } else {
        docker-compose -f docker-compose.infra.yml down
        Write-Success "Инфраструктура остановлена"
    }
} else {
    Write-Step "Останавливаем все сервисы..."
    if ($Clean) {
        docker-compose down -v
        Write-Success "Все сервисы остановлены, тома удалены"
    } else {
        docker-compose down
        Write-Success "Все сервисы остановлены"
    }
}

Show-Containers