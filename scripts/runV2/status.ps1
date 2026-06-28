# СТАТУС СЕРВИСОВ

param(
    [switch]$Watch   # Автообновление
)

. .\lib\functions.ps1

function Show-Status {
    Clear-Host
    Write-Section "СТАТУС СЕРВИСОВ"
    Show-Containers

    # Проверка здоровья
    Write-Section "ЗДОРОВЬЕ СЕРВИСОВ"
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

    foreach ($svc in $services) {
        try {
            $response = Invoke-RestMethod -Uri $svc.Url -TimeoutSec 2 -ErrorAction Stop
            if ($response.status -eq 'UP') {
                Write-Success "$($svc.Name): UP"
            } else {
                Write-Warning "$($svc.Name): $($response.status)"
            }
        } catch {
            Write-Error "$($svc.Name): DOWN"
        }
    }
}

if ($Watch) {
    while ($true) {
        Show-Status
        Write-Color "`nОбновление каждые 5 секунд. Нажмите Ctrl+C для выхода" $Script:Colors.Info
        Start-Sleep -Seconds 5
    }
} else {
    Show-Status
}