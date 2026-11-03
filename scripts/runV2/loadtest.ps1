# loadtest.ps1
# УПРАВЛЕНИЕ НАГРУЗОЧНЫМ ТЕСТИРОВАНИЕМ (k6, Gatling, JMeter)

param(
    [string]$Tool,
    [string]$TestFile,
    [switch]$Up,
    [switch]$Down,
    [switch]$Status,
    [switch]$List
)

. .\lib\functions.ps1

$composeLoadtest = "docker-compose.loadtest.yml"
$projectRoot = Resolve-Path "..\.."

Write-Section "НАГРУЗОЧНОЕ ТЕСТИРОВАНИЕ"

# --- Проверки ---
if (-not (Test-Docker)) {
    Write-Error "Docker не установлен!"
    exit 1
}

function Test-MainStack {
    $network = docker network ls --filter "name=restaurant-network" --format "{{.Name}}" 2>$null
    if (-not $network) {
        Write-Warning "Сеть restaurant-network не найдена!"
        Write-Color "  Сначала запустите основной стек: .\start.ps1 full" $Script:Colors.Info
        return $false
    }
    return $true
}

if (-not (Test-Path "$projectRoot\$composeLoadtest")) {
    Write-Error "Файл $composeLoadtest не найден в $projectRoot"
    Write-Color "  Создайте его в корне проекта" $Script:Colors.Info
    exit 1
}

# --- ФУНКЦИИ (ВСЕ ЗДЕСЬ, ДО ВЫЗОВА) ---

function Show-Tests {
    Write-Color "`n  Доступные тесты k6:" $Script:Colors.Info
    if (Test-Path "$projectRoot\performance-tests\k6") {
        Get-ChildItem "$projectRoot\performance-tests\k6\*.js" | ForEach-Object {
            Write-Color "    - $($_.Name)" $Script:Colors.Info
        }
    } else {
        Write-Color "    (папка performance-tests/k6 не найдена)" $Script:Colors.Warning
    }

    Write-Color "`n  Доступные тесты JMeter:" $Script:Colors.Info
    if (Test-Path "$projectRoot\performance-tests\jmeter") {
        Get-ChildItem "$projectRoot\performance-tests\jmeter\*.jmx" | ForEach-Object {
            Write-Color "    - $($_.Name)" $Script:Colors.Info
        }
    } else {
        Write-Color "    (папка performance-tests/jmeter не найдена)" $Script:Colors.Warning
    }

    Write-Color "`n  Доступные симуляции Gatling:" $Script:Colors.Info
    if (Test-Path "$projectRoot\performance-tests\gatling\simulations") {
        Get-ChildItem "$projectRoot\performance-tests\gatling\simulations\*" | ForEach-Object {
            Write-Color "    - $($_.Name)" $Script:Colors.Info
        }
    } else {
        Write-Color "    (папка performance-tests/gatling/simulations не найдена)" $Script:Colors.Warning
    }
}

function Show-LoadtestStatus {
    Write-Color "`n  Статус генераторов нагрузки:" $Script:Colors.Info
    $containers = @("restaurant-k6", "restaurant-gatling", "restaurant-jmeter")
    foreach ($c in $containers) {
        $status = docker ps --filter "name=$c" --format "{{.Status}}" 2>$null
        if ($status) {
            Write-Success "  $c - $status"
        } else {
            $exists = docker ps -a --filter "name=$c" --format "{{.Status}}" 2>$null
            if ($exists) {
                Write-Warning "  $c - ОСТАНОВЛЕН"
            } else {
                Write-Color "  $c - НЕ СОЗДАН" $Script:Colors.Info
            }
        }
    }
}

function Start-Loadtest {
    if (-not (Test-MainStack)) { return }
    Write-Step "Поднимаем генераторы нагрузки..."
    Push-Location $projectRoot
    try {
        docker-compose -f $composeLoadtest up -d
        if ($LASTEXITCODE -eq 0) {
            Write-Success "Генераторы запущены"
        } else {
            Write-Error "Ошибка запуска"
        }
    } finally {
        Pop-Location
    }
}

function Stop-Loadtest {
    Write-Step "Останавливаем генераторы нагрузки..."
    Push-Location $projectRoot
    try {
        docker-compose -f $composeLoadtest down
        Write-Success "Генераторы остановлены"
    } finally {
        Pop-Location
    }
}

function Invoke-K6Test {
    param([string]$File = "auth-load-test.js")
    Write-Step "Запуск k6: $File"
    docker exec -it restaurant-k6 k6 run "/scripts/$File"
}

function Invoke-JMeterTest {
    param([string]$File = "auth-load-test.jmx")
    $timestamp = Get-Date -Format "yyyy-MM-dd_HH-mm-ss"
    Write-Step "Запуск JMeter: $File"
    docker exec -it restaurant-jmeter jmeter -n -t "/tests/$File" -l "/results/result-$timestamp.jtl" -e -o "/results/html-$timestamp"
    Write-Success "HTML-отчёт: performance-tests/jmeter/results/html-$timestamp/"
}

function Invoke-GatlingTest {
    param([string]$Class = "ApiLoadTest")
    Write-Step "Запуск Gatling: $Class"
    docker exec -it restaurant-gatling gatling.sh -s $Class
    Write-Success "HTML-отчёт: performance-tests/gatling/results/"
}

# --- ЛОГИКА ВЫЗОВА (В КОНЦЕ) ---

if ($List)    { Show-Tests;         exit 0 }
if ($Status)  { Show-LoadtestStatus; exit 0 }
if ($Up)      { Start-Loadtest;     exit 0 }
if ($Down)    { Stop-Loadtest;      exit 0 }

if ($Tool) {
    switch ($Tool.ToLower()) {
        "k6"      { Invoke-K6Test      -File $TestFile }
        "jmeter"  { Invoke-JMeterTest  -File $TestFile }
        "gatling" { Invoke-GatlingTest -Class $TestFile }
        default   { Write-Error "Неизвестный инструмент: $Tool (k6, jmeter, gatling)" }
    }
} else {
    Write-Color "`n  Использование:" $Script:Colors.Info
    Write-Color "    .\loadtest.ps1 -Up" $Script:Colors.Info
    Write-Color "    .\loadtest.ps1 -Down" $Script:Colors.Info
    Write-Color "    .\loadtest.ps1 -Status" $Script:Colors.Info
    Write-Color "    .\loadtest.ps1 -List" $Script:Colors.Info
    Write-Color "    .\loadtest.ps1 -Tool k6 -TestFile auth-load-test.js" $Script:Colors.Info
    Write-Color "    .\loadtest.ps1 -Tool jmeter -TestFile auth-load-test.jmx" $Script:Colors.Info
    Write-Color "    .\loadtest.ps1 -Tool gatling -TestFile ApiLoadTest" $Script:Colors.Info
}