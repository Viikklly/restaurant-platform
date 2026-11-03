// Тест: создание заказов через order-service
// Нагружает: PostgreSQL INSERT (orders + order_items), Kafka producer
//
// Запуск:
//   docker exec restaurant-k6 k6 run /scripts/scenarios/order/1-order-create.js
//   make order-create

import http from 'k6/http';
import { sleep, check } from 'k6';
import { Trend, Counter } from 'k6/metrics';
import { BASE_URL_ORDER } from '../../lib/config.js';
import { headers, randInt } from '../../lib/helpers.js';
import { generateOrderPayload } from '../../lib/data-generator.js';

const createDur = new Trend('order_create_duration', true);
const createErr = new Counter('order_create_errors');

export const options = {
    scenarios: {
        create_orders: {
            executor: 'ramping-vus',
            startVUs: 1,
            stages: [
                { duration: '30s', target: 10 },
                { duration: '1m',  target: 50 },
                { duration: '3m',  target: 50 },
                { duration: '30s', target: 0  },
            ],
        },
    },
    thresholds: {
        'http_req_duration{endpoint:create_order}': ['p(95)<500', 'p(99)<1000'],
        'http_req_failed':                          ['rate<0.01'],
        'order_create_errors':                      ['count<10'],
    },
};

export default function () {
    const userId = randInt(1, 1000);
    const res = http.post(
        `${BASE_URL_ORDER}/api/orders`,
        JSON.stringify(generateOrderPayload(userId)),
        { headers: headers(userId), tags: { endpoint: 'create_order' } }
    );

    createDur.add(res.timings.duration);

    const ok = check(res, {
        '201':            (r) => r.status === 201,
        'has order id':   (r) => r.json('id') !== undefined,
        'status PENDING': (r) => r.json('status') === 'PENDING',
    });
    if (!ok) {
        createErr.add(1);
        if (res.status !== 201) {
            console.log(`create FAILED: status=${res.status} body=${res.body.substring(0, 300)}`);
        }
    }

    sleep(Math.random() * 0.8 + 0.2);
}