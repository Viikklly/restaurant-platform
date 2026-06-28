# ТЕСТИРОВАНИЕ СЕРВИСОВ

param(
    [string]$Service,   # Конкретный сервис
    [switch]$All        # Все сервисы
)

. .\lib\functions.ps1

Write-Section "ТЕСТИРОВАНИЕ"

$services = @(
    @{Name="Eureka"; Url="http://localhost:8761/actuator/health"},
    @{Name="Config"; Url="http://localhost:8888/actuator/health"},
    @{Name="Gateway"; Url="http://localhost:8090/actuator/health"},
    @{Name="Auth"; Url="http://localhost:8081/actuator/health"},
    @{Name="Order"; Url="http://localhost:8082/actuator/health"},
    @{Name="Kitchen"; Url="http://localhost:8083/actuator/health"},
    @{Name="Payment"; Url="http://localhost:8084/actuator/health"},
    @{Name="Notification"; Url="http://localhost:8086/actuator/health"}
)

if ($Service) {
    $services = $services | Where-Object { $_.Name -eq $Service }
    if (-not $services) {
        Write-Error "Сервис '$Service' не найден"
        exit 1
    }
}

$success = 0
$total = $services.Count

foreach ($svc in $services) {
    try {
        $response = Invoke-WebRequest -Uri $svc.Url -TimeoutSec 5 -UseBasicParsing
        if ($response.StatusCode -eq 200) {
            Write-Success "$($svc.Name): OK"
            $success++
        } else {
            Write-Error "$($svc.Name): Status $($response.StatusCode)"
        }
    } catch {
        Write-Error "$($svc.Name): $($_.Exception.Message)"
    }
}

Write-Section "РЕЗУЛЬТАТ"
Write-Color "  Успешно: $success из $total" $Script:Colors.Info
if ($success -eq $total) {
    Write-Success "Все сервисы работают!"
} else {
    Write-Warning "Некоторые сервисы недоступны"
}