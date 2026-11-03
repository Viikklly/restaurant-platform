#!/bin/bash
# startForBash/bin/full.sh

START_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="$START_DIR/../lib"
source "$LIB_DIR/common.sh"
source "$LIB_DIR/stack.sh"
source "$LIB_DIR/ui.sh"
source "$LIB_DIR/health.sh"

stack_full