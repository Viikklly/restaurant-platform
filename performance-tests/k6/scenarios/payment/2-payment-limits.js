
// scenarios/payment/2-payment-limits.js
// Нагрузка на лимит 10000: все заказы должны упасть в FAILED

import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE_URL_ORDER } from '../../lib/config.js';
import { headers, randInt } from '../../lib/helpers.js';
import { generateHugeOrderPayload } from '../../lib/data-generator.js';

const limitReached = new Counter('payment_limit_triggered');

export const options = {
    scenarios: {
        limit_stress: {
            executor: 'constant-arrival-rate',
            rate: 30, // 30 заказов в секунду
            timeUnit: '1s',
            duration: '1m',
            preAllocatedVUs: 20,
            maxVUs: 50,
        },
    },
    thresholds: {
        'http_req_duration{endpoint:create_huge}': ['p(95)<1000'],
    },
};

export default function () {
    const userId = randInt(1, 50);

    // Создаем заказ на 21 пиццу (сумма > 10000)
    const payload = generateHugeOrderPayload(userId);
    const res = http.post(
        `${BASE_URL_ORDER}/api/orders`,
        JSON.stringify(payload),
        { headers: headers(userId), tags: { endpoint: 'create_huge' } }
    );

    if (res.status === 201) {
        limitReached.add(1);
        // Можно добавить проверку через GET /api/payments/order/{id}
    }
}