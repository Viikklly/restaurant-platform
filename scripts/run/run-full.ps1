# Полный запуск

param([switch]$Clean, [switch]$Build)

. .\run-common.ps1

Write-SectionHeader "ПОЛНЫЙ ЗАПУСК"

if (-not (Test-DockerInstalled)) {
    Write-Error "Docker не установлен!"
    exit 1
}

Write-Step "Останавливаем контейнеры..."
docker-compose down 2>&1 | Out-Null

if ($Clean) {
    Write-Step "Удаляем тома..."
    docker-compose down -v 2>&1 | Out-Null
}

if ($Build) {
    Write-Step "Сборка образов..."
    docker-compose build 2>&1 | Out-Null
}

Write-Step "Запускаем БД и брокеры..."
docker-compose up -d postgres-auth postgres-order postgres-kitchen postgres-payment redis kafka
Write-Success "БД и брокеры запущены"

Start-Sleep -Seconds 15






# с проверкой готовности
Write-Step "Запускаем мониторинг..."
docker-compose up -d prometheus grafana jaeger
Write-Success "Мониторинг запущен"

# Ждём, пока Prometheus и Grafana будут готовы
Write-Step "Ожидаем готовности Prometheus..."
$prometheusReady = $false
for ($i = 0; $i -lt 30; $i++) {
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:9090/-/ready" -TimeoutSec 2 -UseBasicParsing
        if ($response.StatusCode -eq 200) {
            $prometheusReady = $true
            Write-Success "Prometheus готов"
            break
        }
    } catch {}
    Start-Sleep -Seconds 2
}
if (-not $prometheusReady) {
    Write-Warning "Prometheus не ответил, продолжаем..."
}

Write-Step "Ожидаем готовности Grafana..."
$grafanaReady = $false
for ($i = 0; $i -lt 30; $i++) {
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:3000/api/health" -TimeoutSec 2 -UseBasicParsing
        if ($response.StatusCode -eq 200) {
            $grafanaReady = $true
            Write-Success "Grafana готова"
            break
        }
    } catch {}
    Start-Sleep -Seconds 2
}
if (-not $grafanaReady) {
    Write-Warning "Grafana не ответила, продолжаем..."
}







Start-Sleep -Seconds 10

Write-Step "Запускаем Discovery Service..."
docker-compose up -d discovery-service
Write-Success "Discovery Service запущен"

Start-Sleep -Seconds 15

Write-Step "Запускаем Config Service..."
docker-compose up -d config-service
Write-Success "Config Service запущен"

Start-Sleep -Seconds 30




# с ожиданием между запусками
Write-Step "Запускаем микросервисы..."
docker-compose up -d gateway-service auth-service order-service kitchen-service payment-service notification-service
Start-Sleep -Seconds 20

# Проверяем регистрацию в Eureka
Write-Step "Проверяем регистрацию в Eureka..."

$expectedServices = @(
    "AUTH-SERVICE",
    "ORDER-SERVICE",
    "KITCHEN-SERVICE",
    "PAYMENT-SERVICE",
    "NOTIFICATION-SERVICE",
    "GATEWAY-SERVICE"
)

try {
    $eurekaApps = Invoke-RestMethod -Uri "http://localhost:8761/eureka/apps" -TimeoutSec 10 -ErrorAction Stop

    # Собираем зарегистрированные сервисы
    $registeredServices = @()
    foreach ($app in $eurekaApps.applications.application) {
        $registeredServices += $app.name
    }

    Write-ColorOutput $Script:InfoColor "`n  Статус регистрации:"

    $allRegistered = $true
    foreach ($svc in $expectedServices) {
        if ($registeredServices -contains $svc) {
            Write-Success " $svc"
        } else {
            Write-Warning "  $svc (не зарегистрирован)"
            $allRegistered = $false
        }
    }

    if ($allRegistered) {
        Write-Success "`n  Все сервисы зарегистрированы в Eureka!"
    } else {
        Write-Warning "`n  Некоторые сервисы не зарегистрированы. Проверьте логи."
    }

} catch {
    Write-Warning "  Не удалось проверить Eureka (сервис ещё не готов или недоступен)"
    Write-ColorOutput $Script:InfoColor "  Проверьте вручную: http://localhost:8761"
}

Write-Success "Микросервисы запущены"





Start-Sleep -Seconds 20

Write-SectionHeader "ГОТОВО!"
Show-ContainerStatus
Show-ServiceUrls
Show-MonitoringStatus
Show-AllUrls