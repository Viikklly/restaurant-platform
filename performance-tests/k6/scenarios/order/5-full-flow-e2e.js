// Главный тест: полный жизненный цикл заказа
//   POST /api/orders → PENDING
//   → Kafka → payment → PAID
//   → Kafka → kitchen OPEN → IN_PROGRESS
//   → cookingTimeMs × dishes (физика кухни)
//   → kitchen READY
//   → Kafka → order READY
//
// МЕТРИКИ:
//   e2e_create_to_ready  — сколько ms живёт заказ (главное)
//   e2e_poll_count       — сколько поллингов делаем
//   e2e_timeout          — сколько заказов не дошли до READY
//   e2e_payment_failed   — сколько ушло в CANCELLED
//
// РЕЖИМЫ ЗАПУСКА:
//   0.1 — Normal  (кухня справляется, p95 ~8s)
//   0.3 — Stress  (предел кухни, p95 ~80s)
//   1.0 — Extreme (перегрузка, таймауты)
//
// Запуск:
//   make e2e         (спросит RATE)
//   make e2e-stress  (RATE=0.3)
//   make e2e-extreme (RATE=1.0)

import http from 'k6/http';
import { sleep, check } from 'k6';
import { Trend, Counter } from 'k6/metrics';
import { BASE_URL_ORDER } from '../../lib/config.js';
import { headers, randInt } from '../../lib/helpers.js';
import { generateSmallOrderPayload } from '../../lib/data-generator.js';

// ── Параметры (читаются один раз при старте k6) ────────────
const RATE             = Number(__ENV.RATE || 0.1);
const DURATION         = __ENV.DURATION || '2m';
const POLL_INTERVAL_MS = Number(__ENV.POLL_INTERVAL_MS || 1000);
const MAX_POLLS        = Number(__ENV.MAX_POLLS || 120);

// k6 требует целое число для rate. Если RATE дробное (0.1),
// меняем timeUnit на '10s' (тогда 1 запрос за 10 секунд = 0.1/сек).
const RATE_TIME_UNIT = Number.isInteger(RATE) ? '1s' : '10s';
const EFFECTIVE_RATE = Number.isInteger(RATE) ? RATE : Math.round(RATE * 10);

// ── Метрики ────────────────────────────────────────────────
const e2eDur       = new Trend('e2e_create_to_ready', true);
const pollCount    = new Trend('e2e_poll_count',      true);
const statusSeen   = new Counter('e2e_status_observed');
const timeoutCount = new Counter('e2e_timeout');
const failedCount  = new Counter('e2e_payment_failed');

// ── setup() выполняется ОДИН раз перед стартом всех VU ─────
export function setup() {
    if (!__ENV.RATE) {
        console.log(`
=========================================================
 ⚠️  RATE не указан. Используем по умолчанию: ${RATE}
 Работает только через Makefile
=========================================================
 Примеры запуска:
   0.1  — Normal  (кухня справляется, p95 ~8s)
   0.3  — Stress  (предел кухни, p95 ~80s)
   1.0  — Extreme (перегрузка, таймауты)

 Как указать:
   make e2e                                  (спросит)
   make e2e-stress                           (RATE=0.3)
   make e2e-extreme                          (RATE=1.0)
=========================================================
        `);
    }
    return {};
}

export const options = {
    scenarios: {
        e2e: {
            executor: 'constant-arrival-rate',
            rate: EFFECTIVE_RATE,
            timeUnit: RATE_TIME_UNIT,
            duration: DURATION,
            preAllocatedVUs: Math.max(20, Math.ceil(RATE * 60)),
            maxVUs: Math.max(50, Math.ceil(RATE * 120)),
        },
    },
    thresholds: {
        'e2e_create_to_ready': ['p(95)<90000', 'p(99)<120000'],
        'e2e_timeout':         ['count<5'],
        'e2e_poll_count':      ['p(95)<60'],
    },
};

function pollStatus(orderId, hdrs) {
    const seen = [];
    let intervalMs = POLL_INTERVAL_MS;

    for (let i = 0; i < MAX_POLLS; i++) {
        const res = http.get(
            `${BASE_URL_ORDER}/api/orders/${orderId}`,
            { headers: hdrs, tags: { step: 'poll' } }
        );

        if (res.status === 200) {
            const status = res.json('status');
            if (!seen.includes(status)) {
                seen.push(status);
                statusSeen.add(1, { status });
            }

            if (status === 'READY' || status === 'DELIVERED') {
                return { status, tries: i + 1, seen };
            }
            if (status === 'CANCELLED') {
                return { status: 'CANCELLED', tries: i + 1, seen };
            }

            if (i > 20 && intervalMs < 5000) {
                intervalMs += 500;
            }
        }
        sleep(intervalMs / 1000);
    }
    return { status: 'TIMEOUT', tries: MAX_POLLS, seen };
}

export default function () {
    const userId = randInt(1, 1000);
    const hdrs   = headers(userId);
    const t0     = Date.now();

    const createRes = http.post(
        `${BASE_URL_ORDER}/api/orders`,
        JSON.stringify(generateSmallOrderPayload(userId)),
        { headers: hdrs, tags: { step: 'create' } }
    );

    if (!check(createRes, { 'create: 201': (r) => r.status === 201 })) {
        console.log(`create FAILED: status=${createRes.status} body=${createRes.body.substring(0, 300)}`);
        failedCount.add(1);
        return;
    }

    const orderId = createRes.json('id');
    const amount  = Number(createRes.json('totalAmount'));

    if (amount >= 10000) {
        console.warn(`Order #${orderId}: ${amount} >= 10000 → payment will FAIL`);
    }

    const result = pollStatus(orderId, hdrs);
    pollCount.add(result.tries);

    if (result.status === 'TIMEOUT') {
        timeoutCount.add(1);
        console.error(
            `Order #${orderId} TIMEOUT after ${result.tries} polls. ` +
            `Seen: ${result.seen.join(' → ')}`
        );
    } else if (result.status === 'READY' || result.status === 'DELIVERED') {
        const elapsed = Date.now() - t0;
        e2eDur.add(elapsed);
    } else {
        failedCount.add(1);
        console.warn(
            `Order #${orderId} → ${result.status}. ` +
            `Seen: ${result.seen.join(' → ')}`
        );
    }
}