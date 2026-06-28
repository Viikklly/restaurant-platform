# Общие функции

$Script:ErrorColor = 'Red'
$Script:SuccessColor = 'Green'
$Script:InfoColor = 'Cyan'
$Script:WarningColor = 'Yellow'

function Write-ColorOutput {
    param([string]$ForegroundColor, [string]$Message)
    $fc = $host.UI.RawUI.ForegroundColor
    $host.UI.RawUI.ForegroundColor = $ForegroundColor
    Write-Output $Message
    $host.UI.RawUI.ForegroundColor = $fc
}

function Write-SectionHeader {
    param([string]$Title)
    Write-ColorOutput $Script:InfoColor "`n" + ("=" * 70)
    Write-ColorOutput $Script:InfoColor "  $Title"
    Write-ColorOutput $Script:InfoColor "=" * 70
}

function Write-Step {
    param([string]$Message)
    Write-ColorOutput $Script:InfoColor "[STEP] $Message"
}

function Write-Success {
    param([string]$Message)
    Write-ColorOutput $Script:SuccessColor " $Message"
}

function Write-Error {
    param([string]$Message)
    Write-ColorOutput $Script:ErrorColor " $Message"
}

function Write-Warning {
    param([string]$Message)
    Write-ColorOutput $Script:WarningColor " $Message"
}

function Test-DockerInstalled {
    try {
        docker --version 2>$null | Out-Null
        return $true
    } catch {
        return $false
    }
}

function Show-ContainerStatus {
    docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
}



function Show-ServiceUrls {
    Write-ColorOutput $Script:InfoColor "`n=== ДОСТУПНЫЕ СЕРВИСЫ ==="
    Write-ColorOutput $Script:InfoColor "  API Gateway:    http://localhost:8090"
    Write-ColorOutput $Script:InfoColor "  Eureka:         http://localhost:8761"
    Write-ColorOutput $Script:InfoColor "  PGAdmin:        http://localhost:5050"
    Write-ColorOutput $Script:InfoColor "  Kafka UI:       http://localhost:8085"
    Write-ColorOutput $Script:InfoColor "  Prometheus:     http://localhost:9090"
    Write-ColorOutput $Script:InfoColor "  Grafana:        http://localhost:3000"
    Write-ColorOutput $Script:InfoColor "  Jaeger:         http://localhost:16686"
    Write-ColorOutput $Script:InfoColor "`n=== МИКРОСЕРВИСЫ ==="
    Write-ColorOutput $Script:InfoColor "  Auth:           http://localhost:8081"
    Write-ColorOutput $Script:InfoColor "  Order:          http://localhost:8082"
    Write-ColorOutput $Script:InfoColor "  Kitchen:        http://localhost:8083"
    Write-ColorOutput $Script:InfoColor "  Payment:        http://localhost:8084"
    Write-ColorOutput $Script:InfoColor "  Notification:   http://localhost:8086"
}

function Show-MonitoringStatus {
    Write-ColorOutput $Script:InfoColor "`n=== СТАТУС МОНИТОРИНГА ==="

    # Проверка Prometheus
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:9090/-/healthy" -TimeoutSec 3 -UseBasicParsing
        if ($response.StatusCode -eq 200) {
            Write-Success "Prometheus: работает (http://localhost:9090)"
        }
    } catch {
        Write-Error "Prometheus: не доступен"
    }

    # Проверка Grafana
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:3000/api/health" -TimeoutSec 3 -UseBasicParsing
        if ($response.StatusCode -eq 200) {
            Write-Success "Grafana: работает (http://localhost:3000, admin/admin)"
        }
    } catch {
        Write-Error "Grafana: не доступна"
    }

    # Проверка Jaeger
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:16686/api/services" -TimeoutSec 3 -UseBasicParsing
        if ($response.StatusCode -eq 200) {
            Write-Success "Jaeger: работает (http://localhost:16686)"
        }
    } catch {
        Write-Error "Jaeger: не доступен"
    }
}

function Show-AllUrls {
    Write-ColorOutput $Script:InfoColor "`n=== ДОСТУПНЫЕ СЕРВИСЫ ==="
    Write-ColorOutput $Script:InfoColor "  API Gateway:    http://localhost:8090"
    Write-ColorOutput $Script:InfoColor "  Eureka:         http://localhost:8761"
    Write-ColorOutput $Script:InfoColor "  PGAdmin:        http://localhost:5050  (admin@restaurant.com/admin)"
    Write-ColorOutput $Script:InfoColor "  Kafka UI:       http://localhost:8085"
    Write-ColorOutput $Script:InfoColor "  Prometheus:     http://localhost:9090"
    Write-ColorOutput $Script:InfoColor "  Grafana:        http://localhost:3000  (admin/admin)"
    Write-ColorOutput $Script:InfoColor "  Jaeger:         http://localhost:16686"
    Write-ColorOutput $Script:InfoColor "`n=== МИКРОСЕРВИСЫ ==="
    Write-ColorOutput $Script:InfoColor "  Auth:           http://localhost:8081/actuator/health"
    Write-ColorOutput $Script:InfoColor "  Order:          http://localhost:8082/actuator/health"
    Write-ColorOutput $Script:InfoColor "  Kitchen:        http://localhost:8083/actuator/health"
    Write-ColorOutput $Script:InfoColor "  Payment:        http://localhost:8084/actuator/health"
    Write-ColorOutput $Script:InfoColor "  Notification:   http://localhost:8086/actuator/health"
}