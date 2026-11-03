#!/bin/bash
# startForBash/lib/clean.sh
# Очистка Docker (только функция)

LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$LIB_DIR/common.sh"

# $1 = force (0/1), $2 = all (0/1)
clean_docker() {
    local force="${1:-0}" all="${2:-0}"

    write_section "ОЧИСТКА DOCKER СИСТЕМЫ"
    check_docker

    write_step "Текущее использование Docker:"
    docker system df

    echo ""
    write_warning "Что будет удалено:"
    write_step "  - Остановленные контейнеры"
    write_step "  - Неиспользуемые сети"
    write_step "  - Неиспользуемые тома"
    if [ "$all" -eq 1 ]; then
        write_warning "  - Неиспользуемые образы (включая непомеченные)"
    else
        write_step "  - Неиспользуемые образы (только зависшие)"
    fi

    if [ "$force" -ne 1 ]; then
        echo ""
        read -r -p "Вы уверены? (y/N): " confirm
        if [ "$confirm" != "y" ] && [ "$confirm" != "Y" ]; then
            write_warning "Очистка отменена"
            return 0
        fi
    fi

    write_step "Выполняем очистку..."
    if [ "$all" -eq 1 ]; then
        write_warning "Выполняется полная очистка системы..."
        docker system prune -a --volumes -f
    else
        write_step "Выполняется стандартная очистка..."
        docker system prune -f
    fi

    write_section "РЕЗУЛЬТАТ ОЧИСТКИ"
    docker system df
    write_success "Очистка завершена!"

    echo ""
    write_step "Дополнительные команды:"
    write_step "  ./clean.sh --all        # Полная очистка (включая все образы)"
    write_step "  ./clean.sh --force      # Без подтверждения"
    write_step "  ./clean.sh --all --force # Полная очистка без подтверждения"
}