# clean.ps1
# ОЧИСТКА DOCKER СИСТЕМЫ

param(
    [switch]$Force,        # Пропустить подтверждение
    [switch]$All           # Полная очистка (включая неиспользуемые образы)
)

. .\lib\functions.ps1

Write-Section "ОЧИСТКА DOCKER СИСТЕМЫ"

# Проверка Docker
if (-not (Test-Docker)) {
    Write-Error "Docker не установлен!"
    exit 1
}

# Показываем текущее использование
Write-Step "Текущее использование Docker:"
docker system df

Write-Color "`n  Что будет удалено:" $Script:Colors.Info
Write-Color "    - Остановленные контейнеры" $Script:Colors.Info
Write-Color "    - Неиспользуемые сети" $Script:Colors.Info
Write-Color "    - Неиспользуемые тома" $Script:Colors.Info
if ($All) {
    Write-Color "    - Неиспользуемые образы (включая непомеченные)" $Script:Colors.Warning
} else {
    Write-Color "    - Неиспользуемые образы (только зависшие)" $Script:Colors.Info
}

if (-not $Force) {
    $confirm = Read-Host "`nВы уверены? (y/N)"
    if ($confirm -ne 'y' -and $confirm -ne 'Y') {
        Write-Warning "Очистка отменена"
        exit 0
    }
}

# Выполняем очистку
Write-Step "Выполняем очистку..."

if ($All) {
    Write-Warning "Выполняется полная очистка системы..."
    docker system prune -a --volumes -f
} else {
    Write-Step "Выполняется стандартная очистка..."
    docker system prune -f
}

# Показываем результат
Write-Section "РЕЗУЛЬТАТ ОЧИСТКИ"
docker system df

Write-Success "`nОчистка завершена!"

# Дополнительные рекомендации
Write-Color "`n  Дополнительные команды:" $Script:Colors.Info
Write-Color "    .\clean.ps1 -All     # Полная очистка (включая все образы)" $Script:Colors.Info
Write-Color "    .\clean.ps1 -Force   # Без подтверждения" $Script:Colors.Info
Write-Color "    .\clean.ps1 -All -Force # Полная очистка без подтверждения" $Script:Colors.Info