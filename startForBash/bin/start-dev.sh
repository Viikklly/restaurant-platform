#!/bin/bash
# startForBash/start-dev.sh

START_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$START_DIR/../lib/common.sh"
source "$START_DIR/../lib/stack.sh"
source "$START_DIR/../lib/ui.sh"
stack_dev