# 3. Проверка таргетов (целей сбора)
$targets = Invoke-RestMethod -Uri "http://localhost:9090/api/v1/targets"

Write-Host "`n=== ТАРГЕТЫ (цели сбора метрик) ===" -ForegroundColor Cyan
foreach ($target in $targets.data.activeTargets) {
    $url = $target.scrapeUrl
    $state = $target.health
    $color = if ($state -eq "up") { "Green" } else { "Red" }
    Write-Host "$url → $state" -ForegroundColor $color
}