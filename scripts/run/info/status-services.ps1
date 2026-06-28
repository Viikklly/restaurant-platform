# Проверка всех сервисов
$query = "up{job='restaurant-services'}"
$response = Invoke-RestMethod -Uri "http://localhost:9090/api/v1/query?query=$query"

Write-Host "=== СТАТУС СЕРВИСОВ ===" -ForegroundColor Cyan
foreach ($result in $response.data.result) {
    $instance = $result.metric.instance
    $up = $result.value[1]
    $status = if ($up -eq "1") { "UP" } else { "DOWN" }
    Write-Host "$instance → $status" -ForegroundColor White
}