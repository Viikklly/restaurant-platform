# Перезапуск

param([switch]$Clean, [switch]$Infra)

. .\run-common.ps1

if ($Infra) {
    & .\run-stop.ps1 -Infra -Clean:$Clean
    & .\run-infra.ps1 -Clean:$Clean
} else {
    & .\run-stop.ps1 -Clean:$Clean
    & .\run-full.ps1 -Clean:$Clean
}