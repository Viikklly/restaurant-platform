// scenarios/payment/1-payment-mixed.js
// Смешанная нагрузка: создание заказов + чтение платежей (Redis/БД)

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE_URL_ORDER, BASE_URL_PAYMENT } from '../../lib/config.js';
import { headers, randInt } from '../../lib/helpers.js';
import { generateSmallOrderPayload, generateHugeOrderPayload } from '../../lib/data-generator.js';

const successCreated = new Counter('payment_success_created');
const failedCreated  = new Counter('payment_failed_created');

export const options = {
    scenarios: {
        // 1. Создаем заказы (они летят в Kafka -> payment-service)
        create_orders: {
            executor: 'ramping-arrival-rate',
            startRate: 5,
            timeUnit: '1s',
            stages: [
                { duration: '30s', target: 20 },
                { duration: '1m',  target: 50 },
                { duration: '30s', target: 50 },
                { duration: '15s', target: 0 },
            ],
            preAllocatedVUs: 50,
            maxVUs: 100,
            exec: 'createOrderFlow',
        },
        // 2. Читаем список платежей (нагрузка на Redis)
        read_payments: {
            executor: 'constant-vus',
            vus: 30,
            duration: '2m',
            exec: 'readPaymentsFlow',
        }
    },
    thresholds: {
        'http_req_duration{endpoint:payment_list}': ['p(95)<200'], // Кэш должен отвечать быстро
        'http_req_duration{endpoint:payment_by_order}': ['p(95)<200'],
    },
};

export function createOrderFlow() {
    // 50% успешные (< 10000), 50% провальные (>= 10000)
    const isSuccess = Math.random() < 0.5;
    const payload = isSuccess
        ? generateSmallOrderPayload(randInt(1, 100))
        : generateHugeOrderPayload(randInt(1, 100));

    const res = http.post(
        `${BASE_URL_ORDER}/api/orders`,
        JSON.stringify(payload),
        { headers: headers(1), tags: { endpoint: 'create_for_payment' } }
    );

    if (res.status === 201) {
        if (isSuccess) successCreated.add(1);
        else failedCreated.add(1);

        // Даем Kafka время обработать и записать в БД
        sleep(1);
    }
}

export function readPaymentsFlow() {
    const action = Math.random();

    if (action < 0.3) {
        // GET /api/payments (кэш payments:all, TTL 30s)
        const res = http.get(`${BASE_URL_PAYMENT}/api/payments`, { headers: headers(1), tags: { endpoint: 'payment_list' } });
        check(res, { '200': (r) => r.status === 200 });
    } else {
        // GET /api/payments/order/{id} (кэш payments:order:{id})
        const orderId = randInt(1, 500);
        const res = http.get(`${BASE_URL_PAYMENT}/api/payments/order/${orderId}`, { headers: headers(1), tags: { endpoint: 'payment_by_order' } });
        check(res, { '200': (r) => r.status === 200 });
    }
    sleep(0.5);
}