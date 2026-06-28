# run-test.ps1 - Тестирование

. .\run-common.ps1

function Test-Service {
    param([string]$Name, [string]$Url)
    try {
        $response = Invoke-WebRequest -Uri $Url -TimeoutSec 5 -UseBasicParsing
        if ($response.StatusCode -eq 200) {
            Write-Success "$Name доступен"
            return $true
        }
    } catch {
        Write-Error "$Name недоступен"
    }
    return $false
}

Write-SectionHeader "ТЕСТИРОВАНИЕ СЕРВИСОВ"

$services = @(
    @{Name="Eureka"; Url="http://localhost:8761/actuator/health"},
    @{Name="Config"; Url="http://localhost:8888/actuator/health"},
    @{Name="Gateway"; Url="http://localhost:8090/actuator/health"},
    @{Name="Auth"; Url="http://localhost:8081/actuator/health"},
    @{Name="Order"; Url="http://localhost:8082/actuator/health"}
)

foreach ($svc in $services) {
    Test-Service -Name $svc.Name -Url $svc.Url
}