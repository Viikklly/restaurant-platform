# check-eureka.ps1
# ПРОВЕРКА РЕГИСТРАЦИИ СЕРВИСОВ В EUREKA

. .\lib\functions.ps1

Write-Section "ПРОВЕРКА EUREKA"

# Список ожидаемых сервисов
$services = @(
    "AUTH-SERVICE",
    "ORDER-SERVICE",
    "KITCHEN-SERVICE",
    "PAYMENT-SERVICE",
    "GATEWAY-SERVICE"
)

try {
    Write-Step "Подключение к Eureka..."
    $eureka = Invoke-RestMethod -Uri "http://localhost:8761/eureka/apps" -TimeoutSec 10 -ErrorAction Stop

    # Получаем список зарегистрированных сервисов
    $registered = @()
    if ($eureka.applications.application) {
        $apps = $eureka.applications.application
        if ($apps -is [array]) {
            $registered = $apps | ForEach-Object { $_.name }
        } else {
            $registered = @($apps.name)
        }
    }

    # Проверяем каждый сервис
    Write-Color "`n  Регистрация в Eureka:" $Script:Colors.Info
    $allOk = $true
    $missingServices = @()

    foreach ($svc in $services) {
        if ($registered -contains $svc) {
            Write-Success "   $svc"
        } else {
            Write-Warning "   $svc (не зарегистрирован)"
            $allOk = $false
            $missingServices += $svc
        }
    }

    # Дополнительная информация
    Write-Color "`n  Всего зарегистрировано: $($registered.Count) сервисов" $Script:Colors.Info

    if ($allOk) {
        Write-Success "`n  Все сервисы зарегистрированы в Eureka! ✓"
        Write-Color "    Eureka UI: http://localhost:8761" $Script:Colors.Info
    } else {
        Write-Warning "`n  Некоторые сервисы не зарегистрированы!"
        Write-Color "    Отсутствуют: $($missingServices -join ', ')" $Script:Colors.Warning
        Write-Color "`n  Рекомендации:" $Script:Colors.Info
        Write-Color "    1. Проверьте, запущены ли сервисы" $Script:Colors.Info
        Write-Color "    2. Проверьте логи проблемных сервисов" $Script:Colors.Info
        Write-Color "    3. Проверьте connectivity к Eureka" $Script:Colors.Info
        Write-Color "    4. Проверьте Eureka UI: http://localhost:8761" $Script:Colors.Info
    }
} catch {
    Write-Error "Не удалось подключиться к Eureka!"
    Write-Color "  Ошибка: $($_.Exception.Message)" $Script:Colors.Error
    Write-Color "`n  Проверьте, что Eureka запущена:" $Script:Colors.Info
    Write-Color "    1. Запустите discovery-service" $Script:Colors.Info
    Write-Color "    2. Проверьте URL: http://localhost:8761" $Script:Colors.Info
    Write-Color "    3. Проверьте логи: docker logs restaurant-discovery" $Script:Colors.Info
}

Write-Color "`n"