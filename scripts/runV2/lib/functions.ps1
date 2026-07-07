# lib/functions.ps1
# ОБЩИЕ ФУНКЦИИ ДЛЯ СКРИПТОВ

# Цвета
$Script:Colors = @{
    Error = 'Red'
    Success = 'Green'
    Info = 'Cyan'
    Warning = 'Yellow'
    Header = 'Magenta'
}

# Функции вывода
function Write-Color {
    param([string]$Message, [string]$Color = 'White')
    $host.UI.RawUI.ForegroundColor = $Color
    Write-Host $Message
    $host.UI.RawUI.ForegroundColor = 'White'
}

function Write-Section {
    param([string]$Title)
    $line = '=' * 70
    Write-Color "`n$line" $Script:Colors.Header
    Write-Color "  $Title" $Script:Colors.Header
    Write-Color "$line" $Script:Colors.Header
}

function Write-Success {
    param([string]$Message)
    Write-Color " $Message" $Script:Colors.Success
}

function Write-Error {
    param([string]$Message)
    Write-Color " $Message" $Script:Colors.Error
}

function Write-Warning {
    param([string]$Message)
    Write-Color " $Message" $Script:Colors.Warning
}

function Write-Step {
    param([string]$Message)
    Write-Color " $Message" $Script:Colors.Info
}

# Проверка Docker
function Test-Docker {
    try {
        docker --version 2>$null | Out-Null
        return $true
    } catch {
        return $false
    }
}

# Показать статус контейнеров
function Show-Containers {
    Write-Section "СТАТУС КОНТЕЙНЕРОВ"
    docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" 2>$null
}

# Показать все URL
function Show-Urls {
    Write-Section "ДОСТУПНЫЕ СЕРВИСЫ"

    Write-Color "`n  ИНФРАСТРУКТУРА:" $Script:Colors.Info
    Write-Color "    PostgreSQL Auth:    localhost:5437 (authdb/postgres/postgres)" $Script:Colors.Info
    Write-Color "    PostgreSQL Order:   localhost:5433 (orderdb/postgres/postgres)" $Script:Colors.Info
    Write-Color "    PostgreSQL Kitchen: localhost:5434 (kitchendb/postgres/postgres)" $Script:Colors.Info
    Write-Color "    PostgreSQL Payment: localhost:5435 (paymentdb/postgres/postgres)" $Script:Colors.Info
    Write-Color "    Redis:              localhost:6379" $Script:Colors.Info
    Write-Color "    Kafka:              localhost:9092" $Script:Colors.Info
    Write-Color "    Zookeeper:          localhost:2181" $Script:Colors.Info

    Write-Color "`n  UI/ВИЗУАЛИЗАЦИЯ:" $Script:Colors.Info
    Write-Color "    PGAdmin:     http://localhost:5050 (admin@restaurant.com/admin)" $Script:Colors.Info
    Write-Color "    Kafka UI:    http://localhost:8085" $Script:Colors.Info

    Write-Color "`n  МОНИТОРИНГ:" $Script:Colors.Info
    Write-Color "    Prometheus:  http://localhost:9090" $Script:Colors.Info
    Write-Color "    Grafana:     http://localhost:3000 (admin/admin)" $Script:Colors.Info
    Write-Color "    Jaeger:      http://localhost:16686" $Script:Colors.Info

    Write-Color "`n  МИКРОСЕРВИСЫ (после запуска):" $Script:Colors.Info
    Write-Color "    Gateway:     http://localhost:8090" $Script:Colors.Info
    Write-Color "    Auth:        http://localhost:8081" $Script:Colors.Info
    Write-Color "    Order:       http://localhost:8082" $Script:Colors.Info
    Write-Color "    Kitchen:     http://localhost:8083" $Script:Colors.Info
    Write-Color "    Payment:     http://localhost:8084" $Script:Colors.Info
    Write-Color "    Notification: http://localhost:8086" $Script:Colors.Info
    Write-Color "    Eureka:      http://localhost:8761" $Script:Colors.Info
    Write-Color "    Config:      http://localhost:8888" $Script:Colors.Info
}

# Проверка готовности сервиса
function Wait-For-Service {
    param(
        [string]$Url,
        [string]$Name,
        [int]$MaxAttempts = 30,
        [int]$Interval = 2
    )

    Write-Step "Ожидаем $Name..."
    for ($i = 1; $i -le $MaxAttempts; $i++) {
        try {
            $response = Invoke-WebRequest -Uri $Url -TimeoutSec 2 -UseBasicParsing -ErrorAction Stop
            if ($response.StatusCode -eq 200) {
                Write-Success "$Name готов"
                return $true
            }
        } catch {
            # Игнорируем ошибки
        }
        if ($i % 5 -eq 0) {
            Write-Warning "Ждем $Name... ($i/$MaxAttempts)"
        }
        Start-Sleep -Seconds $Interval
    }
    Write-Warning "$Name не ответил, продолжаем..."
    return $false
}


# Проверка готовности мониторинга
function Check-Monitoring {
    Write-Step "Проверяем компоненты мониторинга..."

    $services = @(
        @{Name="Prometheus"; Url="http://localhost:9090/-/healthy"},
        @{Name="Grafana"; Url="http://localhost:3000/api/health"},
        @{Name="Jaeger"; Url="http://localhost:16686/api/services"}
    )

    $allOk = $true
    Write-Color "`n  Статус мониторинга:" $Script:Colors.Info

    foreach ($svc in $services) {
        try {
            $response = Invoke-WebRequest -Uri $svc.Url -TimeoutSec 3 -UseBasicParsing -ErrorAction Stop
            if ($response.StatusCode -eq 200) {
                Write-Success "$($svc.Name): готов"
            } else {
                Write-Warning "$($svc.Name): Status $($response.StatusCode)"
                $allOk = $false
            }
        } catch {
            Write-Warning "$($svc.Name): не отвечает"
            $allOk = $false
        }
    }

    if ($allOk) {
        Write-Success "`n  Все компоненты мониторинга работают!"
    } else {
        Write-Warning "`n  Некоторые компоненты мониторинга недоступны. "
        Write-Warning "`n Необходимо дождаться запуска. Через 1 минуту проверьте состояние мониторина "
    }
}