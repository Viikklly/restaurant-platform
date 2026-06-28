$alerts = Invoke-RestMethod -Uri "http://localhost:9090/api/v1/alerts"

if ($alerts.data.alerts.Count -gt 0) {
    Write-Host "`n АКТИВНЫЕ АЛЕРТЫ:" -ForegroundColor Yellow
    foreach ($alert in $alerts.data.alerts) {
        Write-Host "  $($alert.labels.alertname): $($alert.labels.instance)" -ForegroundColor Red
    }
} else {
    Write-Host "`n НЕТ АКТИВНЫХ АЛЕРТОВ" -ForegroundColor Green
}