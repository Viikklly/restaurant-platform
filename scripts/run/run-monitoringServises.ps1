# Проверка всех сервисов
$services = @(
    "http://localhost:8081/actuator/health",
    "http://localhost:8082/actuator/health",
    "http://localhost:8083/actuator/health",
    "http://localhost:8084/actuator/health",
    "http://localhost:8090/actuator/health"
)

foreach ($url in $services) {
    try {
        $response = Invoke-RestMethod -Uri $url -ErrorAction Stop
        Write-Host " $url : $($response.status)" -ForegroundColor Green
    } catch {
        Write-Host "$url : не отвечает" -ForegroundColor Red
    }
}