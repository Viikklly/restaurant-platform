#!/bin/bash
# startForBash/lib/colors.sh
# ТОЛЬКО цвета и базовые функции вывода

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
NC='\033[0m'

write_section() {
    echo -e "\n${MAGENTA}======================================================================${NC}"
    echo -e "${MAGENTA}  $1${NC}"
    echo -e "${MAGENTA}======================================================================${NC}"
}
write_step()    { echo -e "${CYAN}▶ $1${NC}"; }
write_success() { echo -e "${GREEN}✔ $1${NC}"; }
write_warning() { echo -e "${YELLOW}⚠ $1${NC}"; }
write_error()   { echo -e "${RED}✘ $1${NC}"; }