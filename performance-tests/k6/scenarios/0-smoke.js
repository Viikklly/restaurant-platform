
// Smoke: быстро проверить, что весь стек жив и отвечает
//
// Проверяет:
//   - /actuator/health всех 4 сервисов
//   - order-service отдаёт меню (items)
//   - order-service создаёт заказ (201)
//   - order-service читает заказ (200)
//   - kitchen-service отдаёт список тикетов (200)
//   - payment-service отдаёт список транзакций (200)
//   - auth-service отвечает на /auth/test (200)
//
// Запуск:
//   docker exec restaurant-k6 k6 run /scripts/scenarios/0-smoke.js
//
// Если падает — не имеет смысла запускать остальные тесты.
import http from 'k6/http';
import { check, fail } from 'k6';
import {
    BASE_URL_AUTH,
    BASE_URL_ORDER,
    BASE_URL_KITCHEN,
    BASE_URL_PAYMENT,
    TEST_USER,
} from '../lib/config.js';
import { headers, authHeaders, randInt } from '../lib/helpers.js';
import { generateSmallOrderPayload } from '../lib/data-generator.js';

export const options = {
    vus: 1,
    iterations: 1,
    thresholds: {
        checks: ['rate==1.0'],   // 100% проверок
    },
};

export default function () {
    const h = headers(1);

    // ── 1. Health endpoints ───────────────────────────────────
    const services = [
        { name: 'auth',    url: `${BASE_URL_AUTH}/actuator/health`    },
        { name: 'order',   url: `${BASE_URL_ORDER}/actuator/health`   },
        { name: 'kitchen', url: `${BASE_URL_KITCHEN}/actuator/health` },
        { name: 'payment', url: `${BASE_URL_PAYMENT}/actuator/health` },
    ];

    for (const s of services) {
        const res = http.get(s.url, { headers: h });
        check(res, {
            [`${s.name} health 200`]: (r) => r.status === 200,
            [`${s.name} status=UP`]:  (r) => r.json('status') === 'UP',
        });
    }

    // ── 2. Меню (items) заполнено ─────────────────────────────
    const items = http.get(`${BASE_URL_ORDER}/api/items?available=true`, { headers: h });
    check(items, {
        'items: 200':        (r) => r.status === 200,
        'items: >= 1':       (r) => Array.isArray(r.json()) && r.json().length >= 1,
        'items: есть Пицца': (r) => r.json().some((i) => i.itemName === 'Пицца'),
    });

    // ── 3. Создание заказа ────────────────────────────────────
    // userId ОБЯЗАТЕЛЬНО в теле (OrderCreateRequestDTO @NotNull)
    const userId = 999;
    const createRes = http.post(
        `${BASE_URL_ORDER}/api/orders`,
        JSON.stringify(generateSmallOrderPayload(userId)),
        { headers: headers(userId), tags: { endpoint: 'smoke_create' } }
    );

    // диагностика: если упадёт — в логах будет тело ответа
    if (createRes.status !== 201) {
        console.log(`create FAILED: status=${createRes.status} body=${createRes.body}`);
    }

    check(createRes, {
        'create: 201':     (r) => r.status === 201,
        'create: has id':  (r) => r.json('id') !== undefined,
        'create: PENDING': (r) => r.json('status') === 'PENDING',
    });

    const orderId = createRes.json('id');
    if (!orderId) {
        fail('create не вернул id — дальнейшие проверки невозможны');
    }

    // ── 4. Чтение заказа (первый GET — cache miss, второй — hit) ─
    const read1 = http.get(`${BASE_URL_ORDER}/api/orders/${orderId}`, { headers: h });
    check(read1, {
        'read1: 200':    (r) => r.status === 200,
        'read1: has id': (r) => r.json('id') === orderId,
    });

    const read2 = http.get(`${BASE_URL_ORDER}/api/orders/${orderId}`, { headers: h });
    check(read2, {
        'read2: 200':    (r) => r.status === 200,
        'read2: faster': (r) => r.timings.duration < 100,
    });

    // ── 5. Kitchen read ───────────────────────────────────────
    const tickets = http.get(`${BASE_URL_KITCHEN}/api/kitchen/tickets`, { headers: h });
    check(tickets, {
        'kitchen tickets: 200':   (r) => r.status === 200,
        'kitchen tickets: array': (r) => Array.isArray(r.json()),
    });

    // ── 6. Payment read ───────────────────────────────────────
    const payments = http.get(`${BASE_URL_PAYMENT}/api/payments`, { headers: h });
    check(payments, {
        'payments: 200': (r) => r.status === 200,
    });

    // ── 7. Auth /auth/test ────────────────────────────────────
    const loginRes = http.post(
        `${BASE_URL_AUTH}/auth/login`,
        JSON.stringify(TEST_USER),
        { headers: authHeaders(), tags: { endpoint: 'smoke_login' } }
    );
    check(loginRes, {
        'login: 200':   (r) => r.status === 200,
        'login: token': (r) => r.json('accessToken') !== undefined,
    });

    const token = loginRes.json('accessToken');
    if (token) {
        const testRes = http.get(
            `${BASE_URL_AUTH}/auth/test`,
            { headers: { 'Authorization': `Bearer ${token}` }, tags: { endpoint: 'smoke_auth_test' } }
        );
        check(testRes, {
            'auth test: 200': (r) => r.status === 200,
        });
    }
}