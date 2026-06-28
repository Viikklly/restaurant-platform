# Интерактивная оболочка

. .\run-common.ps1

function Show-Menu {
    Clear-Host
    Write-SectionHeader "УПРАВЛЕНИЕ МИКРОСЕРВИСАМИ"
    Write-Host ""
    Write-Host "  1. Полный запуск всех сервисов"
    Write-Host "  2. Запуск инфраструктуры"
    Write-Host "  3. Быстрый старт для разработки"
    Write-Host "  4. Статус контейнеров"
    Write-Host "  5. Логи"
    Write-Host "  6. Остановка всех сервисов"
    Write-Host "  7. Перезапуск"
    Write-Host "  8. Полная очистка"
    Write-Host "  9. Проверка здоровья"
    Write-Host " 10. Статус мониторинга"
    Write-Host " 11. Показать все URL"
    Write-Host " 12. Открыть Grafana в браузере"
    Write-Host "  0. Выход"
    Write-Host ""
}

function Wait-ForEnter {
    Read-Host "Нажмите Enter для продолжения"
}

do {
    Show-Menu
    $choice = Read-Host "Выберите действие (0-9)"

    switch ($choice) {
        "1" { & .\run-full.ps1; Wait-ForEnter }
        "2" { & .\run-infra.ps1; Wait-ForEnter }
        "3" { & .\run-dev.ps1; Wait-ForEnter }
        "4" { & .\run-status.ps1; Wait-ForEnter }
        "5" {
            $service = Read-Host "Введите имя сервиса (или Enter для всех)"
            if ($service) { & .\run-logs.ps1 $service -Follow }
            else { & .\run-logs.ps1 -All -Follow }
        }
        "6" { & .\run-stop.ps1; Wait-ForEnter }
        "7" {
            $clean = Read-Host "Удалить тома? (y/n)"
            if ($clean -eq 'y') { & .\run-restart.ps1 -Clean }
            else { & .\run-restart.ps1 }
            Wait-ForEnter
        }
        "8" {
            Write-Warning "Удалить ВСЕ контейнеры и тома?"
            $confirm = Read-Host "Введите 'yes' для подтверждения"
            if ($confirm -eq 'yes') {
                & .\run-stop.ps1 -Clean
                docker system prune -f
                Write-Success "Очистка выполнена"
            }
            Wait-ForEnter
        }
        "9" {
            Write-SectionHeader "ПРОВЕРКА ЗДОРОВЬЯ"
            docker inspect --format='{{.Name}} - {{.State.Health.Status}}' $(docker ps -q) 2>$null
            Wait-ForEnter
        }
        "10" {
            Show-MonitoringStatus
            Wait-ForEnter
        }
        "11" {
            Show-AllUrls
            Wait-ForEnter
        }
        "12" {
            Start-Process "http://localhost:3000"
            Write-Success "Grafana открыта в браузере"
            Wait-ForEnter
        }
        "0" {
            Write-Success "До свидания!"
            exit 0
        }
    }
} while ($true)