# ИНТЕРАКТИВНОЕ МЕНЮ

. .\lib\functions.ps1

function Show-Menu {
    Clear-Host
    Write-Section "УПРАВЛЕНИЕ МИКРОСЕРВИСАМИ"
    Write-Color @"

  1. Полный запуск всех сервисов
  2. Запуск инфраструктуры (разработка)
  3. Остановка всех сервисов
  4. Остановка с очисткой томов
  5. Статус контейнеров
  6. Статус с автообновлением
  7. Логи (все сервисы)
  8. Логи (конкретный сервис)
  9. Тестирование всех сервисов
 10. Показать все URL
 11. Запуск только мониторинга
 12. Логи мониторинга
 13. Проверить мониторинг
 14. Проверить Eureka
 15. Очистка Docker системы
  0. Выход

"@ $Script:Colors.Info
}

function Wait-Enter {
    Read-Host "`nНажмите Enter для продолжения"
}

do {
    Show-Menu
    $choice = Read-Host "Выберите действие (0-15)"

    switch ($choice) {
        "1" {
            & .\start-full.ps1
            Wait-Enter
        }
        "2" {
            & .\start-dev.ps1
            Wait-Enter
        }
        "3" {
            & .\stop.ps1
            Wait-Enter
        }
        "4" {
            & .\stop.ps1 -Clean
            Wait-Enter
        }
        "5" {
            & .\status.ps1
            Wait-Enter
        }
        "6" {
            & .\status.ps1 -Watch
        }
        "7" {
            & .\logs.ps1 -All -Follow
        }
        "8" {
            $svc = Read-Host "Введите имя сервиса"
            if ($svc) {
                & .\logs.ps1 -Service $svc -Follow
            }
        }
        "9" {
            & .\test.ps1 -All
            Wait-Enter
        }
        "10" {
            Show-Urls
            Wait-Enter
        }
        "11" {
            & .\start-monitoring.ps1
            Wait-Enter
        }
        "12" {
            & .\logs.ps1 -Service prometheus -Follow
        }
        "13" {
            Check-Monitoring
            Wait-Enter
        }
        "14" {
            & .\check-eureka.ps1
            Wait-Enter
        }
        "15" {
            & .\clean.ps1
            Wait-Enter
        }
        "0" {
            Write-Success "До свидания!"
            exit 0
        }
        default {
            Write-Error "Неверный выбор!"
            Start-Sleep -Seconds 1
        }
    }
} while ($true)