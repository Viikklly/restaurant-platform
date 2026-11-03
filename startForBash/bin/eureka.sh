#!/bin/bash
# startForBash/check-eureka.sh
START_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$START_DIR/../lib/common.sh"
source "$START_DIR/../lib/health.sh"
eureka_check