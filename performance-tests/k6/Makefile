# performance-tests/k6/Makefile
# Управление нагрузочными тестами
#
#   make help           — список команд
#   make smoke          — проверить, что стек жив
#   make all            — все основные тесты
#   make e2e            — E2E create → READY (интерактивный RATE)

# ─── Настройки ─────────────────────────────────────────────
K6_CONTAINER ?= restaurant-k6
K6           := docker exec $(K6_CONTAINER) k6
SCRIPTS_DIR  := /scripts/scenarios
RESULTS_DIR  := /results
TESTID       ?= $(shell date +%Y%m%d-%H%M%S)

# Переменные для Prometheus
PROMETHEUS_URL ?= http://prometheus:9090
K6_ENV         := K6_PROMETHEUS_RW_SERVER_URL=$(PROMETHEUS_URL) K6_PROMETHEUS_RW_TREND_STATS='p(50),p(95),p(99),avg'

# запуск k6 с отправкой в Prometheus
K6_RW := docker exec $(K6_CONTAINER) sh -c "$(K6_ENV) k6 run --out experimental-prometheus-rw"

CYAN  := \033[36m
GREEN := \033[32m
RESET := \033[0m

.PHONY: help
help:   ## Показать список команд
	@echo "$(CYAN)k6 Load Tests — Restaurant Platform$(RESET)"
	@echo ""
	@grep -E '^[a-zA-Z0-9_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "  $(GREEN)%-20s$(RESET) %s\n", $$1, $$2}'

# ─── Инфраструктура ────────────────────────────────────────
.PHONY: up
up:     ## Поднять контейнеры нагрузочного тестирования
	docker compose -f docker-compose.loadtest.yml up -d

.PHONY: down
down:   ## Остановить контейнеры нагрузочного тестирования
	docker compose -f docker-compose.loadtest.yml down

.PHONY: status
status: ## Проверить, что k6 запущен
	@docker ps --filter name=$(K6_CONTAINER) --format "table {{.Names}}\t{{.Status}}"

.PHONY: clean-results
clean-results: ## Удалить результаты прогонов
	rm -rf results/*.json results/*.html results/*.log
	@echo "Результаты очищены"

.PHONY: lint
lint:   ## Проверить синтаксис всех k6-скриптов
	@for f in $(shell find scenarios -name '*.js' 2>/dev/null); do \
		docker exec $(K6_CONTAINER) k6 inspect "/scripts/$$f" > /dev/null 2>&1 \
			&& echo "✓ $$f" \
			|| echo "✗ $$f"; \
	done

# ─── Smoke ─────────────────────────────────────────────────
.PHONY: smoke
smoke:  ## Smoke: проверить, что весь стек жив
	$(K6) run $(SCRIPTS_DIR)/0-smoke.js

# ─── Auth ──────────────────────────────────────────────────
.PHONY: auth-test
auth-test:      ## auth: GET /auth/test (простейший)
	$(K6) run $(SCRIPTS_DIR)/auth/5-auth-test.js

.PHONY: auth-login
auth-login:     ## auth: POST /auth/login (BCrypt)
	$(K6) run $(SCRIPTS_DIR)/auth/1-login.js

.PHONY: auth-validate
auth-validate:  ## auth: POST /auth/validate (HMAC)
	$(K6) run $(SCRIPTS_DIR)/auth/4-validate.js

.PHONY: auth-register
auth-register:  ## auth: POST /auth/register (INSERT + BCrypt)
	$(K6) run $(SCRIPTS_DIR)/auth/2-register.js

.PHONY: auth-refresh
auth-refresh:   ## auth: POST /auth/refresh (refresh JWT)
	$(K6) run $(SCRIPTS_DIR)/auth/3-refresh.js

.PHONY: auth-realistic
auth-realistic: ## auth: mix (login + register + validate)
	$(K6) run $(SCRIPTS_DIR)/auth/6-auth-realistic.js

# ─── Order ─────────────────────────────────────────────────
.PHONY: order-create
order-create:   ## order: POST /api/orders (ramp до 50 VU)
	$(K6) run $(SCRIPTS_DIR)/order/1-order-create.js

.PHONY: order-read
order-read:     ## order: GET /api/orders/{id} (hot vs cold)
	$(K6) run $(SCRIPTS_DIR)/order/2-order-read.js

.PHONY: order-stress
order-stress:   ## order: stress до 500 rps (намеренно ломает)
	$(K6) run $(SCRIPTS_DIR)/order/3-order-stress.js

# ─── Kitchen ───────────────────────────────────────────────
.PHONY: kitchen-read
kitchen-read:   ## kitchen: GET /tickets/* (smoke)
	$(K6) run $(SCRIPTS_DIR)/kitchen/1-kitchen-read.js

.PHONY: kitchen-by-id
kitchen-by-id:  ## kitchen: GET /tickets/{id} (сам создаёт тикеты)
	$(K6) run $(SCRIPTS_DIR)/kitchen/2-kitchen-by-id.js

.PHONY: kitchen-mixed
kitchen-mixed:  ## kitchen: integration (order → kitchen)
	$(K6) run $(SCRIPTS_DIR)/kitchen/3-kitchen-mixed.js

# ─── Payment ───────────────────────────────────────────────
.PHONY: payment-read
payment-read:   ## payment: GET /api/payments/* (только чтение)
	$(K6) run $(SCRIPTS_DIR)/payment/1-payment-read.js

.PHONY: payment-mixed
payment-mixed:  ## payment: write + read (смешанная нагрузка)
	$(K6) run $(SCRIPTS_DIR)/payment/6-payment-mixed.js

# ─── E2E ───────────────────────────────────────────────────
.PHONY: e2e
e2e:            ## E2E: интерактивный ввод RATE
	@read -p "Введите RATE (Enter=0.1, 0.3 - stress, 1.0 - extreme): " rate; \
	if [ -z "$$rate" ]; then rate="0.1"; fi; \
	echo "▶ Запуск E2E с RATE=$$rate"; \
	$(K6) run -e RATE=$$rate $(SCRIPTS_DIR)/order/5-full-flow-e2e.js

.PHONY: e2e-stress
e2e-stress:     ## E2E: стресс (RATE=0.3, предел кухни)
	$(K6) run -e RATE=0.3 $(SCRIPTS_DIR)/order/5-full-flow-e2e.js

.PHONY: e2e-extreme
e2e-extreme:    ## E2E: экстрим (RATE=1.0, перегрузка)
	$(K6) run -e RATE=1.0 $(SCRIPTS_DIR)/order/5-full-flow-e2e.js

# ─── С Prometheus (для Grafana) ────────────────────────────
.PHONY: smoke-rw
smoke-rw:       ## smoke + Prometheus remote-write
	$(K6_RW) --tag testid=smoke-$(TESTID) $(SCRIPTS_DIR)/0-smoke.js

.PHONY: order-create-rw
order-create-rw: ## order-create + Prometheus
	$(K6_RW) --tag testid=order-create-$(TESTID) $(SCRIPTS_DIR)/order/1-order-create.js

.PHONY: kitchen-by-id-rw
kitchen-by-id-rw: ## kitchen-by-id + Prometheus
	$(K6_RW) --tag testid=kitchen-by-id-$(TESTID) $(SCRIPTS_DIR)/kitchen/2-kitchen-by-id.js

.PHONY: payment-mixed-rw
payment-mixed-rw: ## payment-mixed + Prometheus
	$(K6_RW) --tag testid=payment-mixed-$(TESTID) $(SCRIPTS_DIR)/payment/6-payment-mixed.js

# Единая цель для E2E + Prometheus
.PHONY: e2e-rw
e2e-rw:         ## E2E + Prometheus (спросит RATE)
	@read -p "Введите RATE для E2E (Enter=0.1, 0.3 - stress, 1.0 - extreme): " rate; \
	if [ -z "$$rate" ]; then rate="0.1"; fi; \
	echo "▶ Запуск E2E с RATE=$$rate + Prometheus"; \
	$(K6_RW) -e RATE=$$rate --tag testid=e2e-rw-$(TESTID) $(SCRIPTS_DIR)/order/5-full-flow-e2e.js

# ─── Web dashboard ─────────────────────────────────────────
.PHONY: smoke-dash
smoke-dash:     ## smoke с web dashboard на :5665
	$(K6) run --out web-dashboard $(SCRIPTS_DIR)/0-smoke.js

# ─── Комбо ─────────────────────────────────────────────────
.PHONY: all
all: smoke auth-login order-create order-read kitchen-by-id payment-read payment-mixed e2e-stress
	@echo ""
	@echo "$(GREEN)✓ Все основные тесты пройдены$(RESET)"

.PHONY: all-auth
all-auth: auth-test auth-login auth-validate auth-refresh auth-realistic
	@echo "$(GREEN)✓ Все auth-тесты пройдены$(RESET)"

.PHONY: all-order
all-order: order-create order-read
	@echo "$(GREEN)✓ Все order-тесты пройдены$(RESET)"

.PHONY: all-kitchen
all-kitchen: kitchen-read kitchen-by-id kitchen-mixed
	@echo "$(GREEN)✓ Все kitchen-тесты пройдены$(RESET)"

.PHONY: all-payment
all-payment: payment-read payment-mixed
	@echo "$(GREEN)✓ Все payment-тесты пройдены$(RESET)"