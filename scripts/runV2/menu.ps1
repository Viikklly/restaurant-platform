# ИНТЕРАКТИВНОЕ МЕНЮ

. .\lib\functions.ps1

function Show-Menu {
    Clear-Host
    Write-Section "УПРАВЛЕНИЕ МИКРОСЕРВИСАМИ"
    Write-Color @"

   ОСНОВНОЕ
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

  МОНИТОРИНГ
 11. Запуск только мониторинга для dev
 12. Логи мониторинга
 13. Проверить мониторинг
 14. Проверить Eureka

 НАГРУЗОЧНОЕ ТЕСТИРОВАНИЕ
 17. Поднять генераторы (k6, Gatling, JMeter)
 (поднимает контейнеры restaurant-k6, restaurant-gatling, restaurant-jmeter через docker-compose.loadtest.yml.
  Они просто ждут (tail -f /dev/null).)

 18. Остановить генераторы(гасит их (docker-compose down).)
 19. Статус генераторов (показывает, запущены ли они.)
 20. Список доступных тестов (выводит список доступных .js, .jmx и симуляций Gatling из папки performance-tests/.)
 21. Запустить тест k6
 22. Запустить тест JMeter
 23. Запустить симуляцию Gatling

 ОЧИСТКА
 15. Очистка Docker системы
 16. Полная очистка Docker (все образы + тома)

  0. Выход

"@ $Script:Colors.Info
}

function Wait-Enter {
    Read-Host "`nНажмите Enter для продолжения"
}

do {
    Show-Menu
    $choice = Read-Host "Выберите действие (0-16)"

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
        "16" {
            & .\clean.ps1 -All -Force
            Wait-Enter
        }

        "17" {
            & .\loadtest.ps1 -Up
            Wait-Enter
        }
        "18" {
            & .\loadtest.ps1 -Down
            Wait-Enter
        }
        "19" {
            & .\loadtest.ps1 -Status
            Wait-Enter
        }
        "20" {
            & .\loadtest.ps1 -List
            Wait-Enter
        }
        "21" {
            & .\loadtest.ps1 -List
            $file = Read-Host "`nВведите имя k6-теста (Enter для auth-load-test.js)"
            if (-not $file) { $file = "auth-load-test.js" }
            & .\loadtest.ps1 -Tool k6 -TestFile $file
            Wait-Enter
        }
        "22" {
            & .\loadtest.ps1 -List
            $file = Read-Host "`nВведите имя JMeter-теста (Enter для auth-load-test.jmx)"
            if (-not $file) { $file = "auth-load-test.jmx" }
            & .\loadtest.ps1 -Tool jmeter -TestFile $file
            Wait-Enter
        }
        "23" {
            & .\loadtest.ps1 -List
            $class = Read-Host "`nВведите имя класса Gatling (Enter для ApiLoadTest)"
            if (-not $class) { $class = "ApiLoadTest" }
            & .\loadtest.ps1 -Tool gatling -TestFile $class
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