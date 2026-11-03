
// Интеграционный тест: создаём заказы (order) → читаем тикеты (kitchen)
// Проверяет, что Kafka-цепочка работает под нагрузкой
//
// Нагружает:
//   - order-service (POST /api/orders) → Kafka → payment → Kafka → kitchen
//   - kitchen-service (GET /tickets)

import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL_ORDER, BASE_URL_KITCHEN } from '../../lib/config.js';
import { headers, pick, randInt } from '../../lib/helpers.js';
import { generateSmallOrderPayload } from '../../lib/data-generator.js';

export const options = {
    scenarios: {
        kafka_flow: {
            executor: 'constant-arrival-rate',
            rate: 10,
            timeUnit: '1s',
            duration: '3m',
            preAllocatedVUs: 30,
            maxVUs: 60,
            exec: 'writeFlow',
        },
        read_flow: {
            executor: 'constant-vus',
            vus: 30,
            duration: '3m',
            exec: 'readFlow',
        },
        status_flow: {
            executor: 'constant-vus',
            vus: 15,
            duration: '3m',
            exec: 'statusFlow',
        },
    },
    thresholds: {
        'http_req_duration{endpoint:create_order}':      ['p(95)<500'],
        'http_req_duration{endpoint:kitchen_list}':      ['p(95)<800'],
        'http_req_duration{endpoint:kitchen_by_status}': ['p(95)<300'],
    },
};

export function writeFlow() {
    const userId = randInt(1, 1000);
    const res = http.post(
        `${BASE_URL_ORDER}/api/orders`,
        JSON.stringify(generateSmallOrderPayload(userId)),
        { headers: headers(userId), tags: { endpoint: 'create_order' } }
    );
    check(res, { '201': (r) => r.status === 201 });
    if (res.status !== 201) {
        console.log(`writeFlow FAILED: status=${res.status} body=${res.body.substring(0, 200)}`);
    }
}

export function readFlow() {
    const res = http.get(
        `${BASE_URL_KITCHEN}/api/kitchen/tickets`,
        { headers: headers(1), tags: { endpoint: 'kitchen_list' } }
    );
    check(res, { '200': (r) => r.status === 200 });
    sleep(0.5);
}

export function statusFlow() {
    const status = pick(['open', 'in-progress', 'ready']);
    const res = http.get(
        `${BASE_URL_KITCHEN}/api/kitchen/tickets/status/${status}`,
        { headers: headers(1), tags: { endpoint: 'kitchen_by_status' } }
    );
    check(res, { '200': (r) => r.status === 200 });
    sleep(0.3);
}