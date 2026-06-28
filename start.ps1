# start.ps1 - Быстрый запуск из корня проекта

param(
    [string]$Command = "menu",  # По умолчанию меню
    [switch]$Clean,
    [switch]$Build,
    [switch]$Infra,
    [switch]$Watch,
    [switch]$All,
    [switch]$Follow,
    [string]$Service,
    [int]$Lines = 50
)

# Путь к папке со скриптами
$scriptsDir = Join-Path $PSScriptRoot "scripts\runV2"

# Карта команд → имена скриптов
$commandMap = @{
    "menu"    = "menu.ps1"
    "full"    = "start-full.ps1"
    "dev"     = "start-dev.ps1"
    "infra"   = "start-dev.ps1"
    "stop"    = "stop.ps1"
    "status"  = "status.ps1"
    "logs"    = "logs.ps1"
    "test"    = "test.ps1"
    "shell"   = "menu.ps1"
}

# Формируем параметры для вызова
$params = @()
if ($Clean)   { $params += "-Clean" }
if ($Build)   { $params += "-Build" }
if ($Infra)   { $params += "-Infra" }
if ($Watch)   { $params += "-Watch" }
if ($All)     { $params += "-All" }
if ($Follow)  { $params += "-Follow" }
if ($Service) { $params += "-Service"; $params += $Service }
if ($Lines -ne 50) { $params += "-Lines"; $params += $Lines }

# Получаем имя скрипта
$scriptName = $commandMap[$Command]
if (-not $scriptName) {
    Write-Host "Неизвестная команда: $Command" -ForegroundColor Red
    Show-Help
    exit 1
}

# Запускаем скрипт
$scriptPath = Join-Path $scriptsDir $scriptName
if (Test-Path $scriptPath) {
    Write-Host "Запуск: $scriptPath" -ForegroundColor Gray
    Write-Host "Параметры: $($params -join ' ')" -ForegroundColor Gray
    Write-Host ""

    # Переходим в папку со скриптами и запускаем
    Push-Location $scriptsDir
    try {
        & ".\$scriptName" @params
    } finally {
        Pop-Location
    }
} else {
    Write-Host "Скрипт не найден: $scriptPath" -ForegroundColor Red
    Show-Help
}

function Show-Help {
    Write-Host ""
    Write-Host "Доступные команды:" -ForegroundColor Yellow
    Write-Host "  menu    - Интерактивное меню (по умолчанию)" -ForegroundColor Gray
    Write-Host "  full    - Полный запуск всех сервисов" -ForegroundColor Gray
    Write-Host "  dev     - Запуск инфраструктуры (разработка)" -ForegroundColor Gray
    Write-Host "  infra   - Запуск только инфраструктуры" -ForegroundColor Gray
    Write-Host "  status  - Статус контейнеров" -ForegroundColor Gray
    Write-Host "  logs    - Просмотр логов" -ForegroundColor Gray
    Write-Host "  stop    - Остановка всех сервисов" -ForegroundColor Gray
    Write-Host "  test    - Тестирование сервисов" -ForegroundColor Gray
    Write-Host "  shell   - Интерактивная оболочка" -ForegroundColor Gray
    Write-Host ""
    Write-Host "Параметры:" -ForegroundColor Yellow
    Write-Host "  -Clean   - Удалить тома" -ForegroundColor Gray
    Write-Host "  -Build   - Пересобрать образы" -ForegroundColor Gray
    Write-Host "  -Watch   - Автообновление статуса" -ForegroundColor Gray
    Write-Host "  -Follow  - Следить за логами" -ForegroundColor Gray
    Write-Host "  -Service - Имя сервиса для логов" -ForegroundColor Gray
    Write-Host "  -Lines   - Количество строк в логах (по умолчанию 50)" -ForegroundColor Gray
    Write-Host ""
    Write-Host "Примеры:" -ForegroundColor Yellow
    Write-Host "  .\start.ps1 full" -ForegroundColor Gray
    Write-Host "  .\start.ps1 full -Clean -Build" -ForegroundColor Gray
    Write-Host "  .\start.ps1 logs -Service auth -Follow" -ForegroundColor Gray
    Write-Host "  .\start.ps1 status -Watch" -ForegroundColor Gray
    Write-Host "  .\start.ps1" -ForegroundColor Gray
}