#!/bin/bash
# startForBash/bin/urls.sh
# Показать все доступные URL
START_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="$START_DIR/../lib"
source "$LIB_DIR/common.sh"
source "$LIB_DIR/ui.sh"

show_urls