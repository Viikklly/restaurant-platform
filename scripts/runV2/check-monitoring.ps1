# check-monitoring.ps1
# ПРОВЕРКА РАБОТОСПОСОБНОСТИ МОНИТОРИНГА

. .\lib\functions.ps1

Write-Section "ПРОВЕРКА МОНИТОРИНГА"

# Список контейнеров мониторинга (обновлён)
$containers = @(
    "restaurant-loki",
    "restaurant-promtail",
    "restaurant-grafana",
    "restaurant-prometheus",
    "restaurant-alertmanager",
    "restaurant-jaeger"
)

Write-Color " КОНТЕЙНЕРЫ:" $Script:Colors.Header
Write-Color "------------------------------------------" $Script:Colors.Header

$allRunning = $true
$notRunning = @()

foreach ($container in $containers) {
    $status = docker ps --filter "name=$container" --format "{{.Status}}" 2>$null
    if ($status) {
        Write-Success "$container - $status"
    } else {
        $exists = docker ps -a --filter "name=$container" --format "{{.Status}}" 2>$null
        if ($exists) {
            Write-Warning "$container - ОСТАНОВЛЕН ($exists)"
            $allRunning = $false
            $notRunning += $container
        } else {
            Write-Error "$container - НЕ СУЩЕСТВУЕТ"
            $allRunning = $false
            $notRunning += $container
        }
    }
}

Write-Color "`n ЭНДПОИНТЫ:" $Script:Colors.Header
Write-Color "------------------------------------------" $Script:Colors.Header

function Check-Url {
    param($Name, $Url, $ExpectedStatus = 200)
    try {
        $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5 -ErrorAction Stop
        $statusCode = $response.StatusCode
        if ($statusCode -eq $ExpectedStatus -or $statusCode -eq 204) {
            Write-Success "$Name - OK (статус $statusCode)"
            return $true
        } else {
            Write-Warning "$Name - статус $statusCode (ожидался $ExpectedStatus)"
            return $false
        }
    } catch {
        Write-Error "$Name - НЕ ДОСТУПЕН"
        return $false
    }
}

$endpoints = @(
    @{Name="Loki"; Url="http://localhost:3100/ready"; Expected=200},
    @{Name="Promtail"; Url="http://localhost:9080/ready"; Expected=200},
    @{Name="Grafana"; Url="http://localhost:3000/api/health"; Expected=200},
    @{Name="Prometheus"; Url="http://localhost:9090/-/healthy"; Expected=200},
    @{Name="Alertmanager"; Url="http://localhost:9093/-/healthy"; Expected=200},
    @{Name="Jaeger"; Url="http://localhost:16686/api/services"; Expected=200}
)

$allOk = $true
foreach ($ep in $endpoints) {
    if (-not (Check-Url -Name $ep.Name -Url $ep.Url -ExpectedStatus $ep.Expected)) {
        $allOk = $false
    }
}

Write-Color "`n СТАТИСТИКА:" $Script:Colors.Header
Write-Color "------------------------------------------" $Script:Colors.Header

# Проверка, что Loki принимает логи
try {
    $timestamp = Get-Date -Format "yyyy-MM-ddTHH:mm:ss.ffffffZ"
    $testLog = '{"streams":[{"stream":{"test":"health-check"},"values":[["' + $timestamp + '","health check log from check-monitoring"]]}]}'
    $response = Invoke-WebRequest -Uri "http://localhost:3100/loki/api/v1/push" -Method POST -Body $testLog -ContentType "application/json" -UseBasicParsing -TimeoutSec 5
    $statusCode = $response.StatusCode
    if ($statusCode -eq 204) {
        Write-Success "Loki принимает логи - OK"
    } else {
        Write-Warning "Loki принимает логи - статус $statusCode"
    }
} catch {
    Write-Error "Loki НЕ принимает логи"
}

Write-Color "`n ПОСЛЕДНИЕ ЛОГИ (ошибки):" $Script:Colors.Header
Write-Color "------------------------------------------" $Script:Colors.Header

$foundErrors = $false
foreach ($container in $containers) {
    $exists = docker ps -a --filter "name=$container" --format "{{.Names}}" 2>$null
    if ($exists) {
        $errors = docker logs --tail 5 $container 2>&1 | Select-String -Pattern "error|Error|ERROR|panic|fatal|FATAL" | Select-Object -First 3
        if ($errors) {
            Write-Color " $container:" $Script:Colors.Warning
            foreach ($err in $errors) {
                Write-Color "   $err" $Script:Colors.Error
            }
            $foundErrors = $true
        }
    }
}

if (-not $foundErrors) {
    Write-Success "Ошибок в логах не найдено"
}

# Итог
Write-Color "`n==========================================" $Script:Colors.Header
if ($allRunning -and $allOk) {
    Write-Success " ВСЕ КОМПОНЕНТЫ МОНИТОРИНГА РАБОТАЮТ!"
    Write-Color "   Grafana: http://localhost:3000 (admin/admin)" $Script:Colors.Info
    Write-Color "   Loki:    http://localhost:3100" $Script:Colors.Info
    Write-Color "   Jaeger:  http://localhost:16686" $Script:Colors.Info
} else {
    Write-Warning " НЕКОТОРЫЕ КОМПОНЕНТЫ НЕ РАБОТАЮТ"
    if ($notRunning.Count -gt 0) {
        Write-Color "   Не запущены: $($notRunning -join ', ')" $Script:Colors.Warning
    }
    Write-Color "`n   Рекомендации:" $Script:Colors.Info
    Write-Color "   1. Проверьте логи: docker logs <container_name>" $Script:Colors.Info
    Write-Color "   2. Перезапустите: .\start-monitoring.ps1" $Script:Colors.Info
    Write-Color "   3. Проверьте файлы конфигурации в ./infrastructure/monitoring/" $Script:Colors.Info
}
Write-Color "==========================================`n" $Script:Colors.Header