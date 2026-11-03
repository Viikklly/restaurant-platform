
// Тест: чтение заказов, горячий vs холодный кэш
// Доказывает эффективность @Cacheable("orders")
//
// Ожидание:
//   hit p95  →  5-20ms (Redis)
//   miss p95 → 50-200ms (PostgreSQL)

import http from 'k6/http';
import { check } from 'k6';
import { Trend, Counter } from 'k6/metrics';
import { BASE_URL_ORDER } from '../../lib/config.js';
import { headers, randInt } from '../../lib/helpers.js';
import { generateSmallOrderPayload } from '../../lib/data-generator.js';

const hitDur   = new Trend('order_read_hit',  true);
const missDur  = new Trend('order_read_miss', true);
const notFound = new Counter('order_read_404');

export function setup() {
    const ids = [];
    for (let i = 0; i < 100; i++) {
        const userId = 1;
        const res = http.post(
            `${BASE_URL_ORDER}/api/orders`,
            JSON.stringify(generateSmallOrderPayload(userId)),
            { headers: headers(userId) }
        );
        if (res.status === 201) {
            const id = res.json('id');
            ids.push(id);
            // прогрев кэша: первый GET → miss → записывает в Redis
            http.get(`${BASE_URL_ORDER}/api/orders/${id}`, { headers: headers(userId) });
        }
    }
    console.log(`setup: ${ids.length} заказов, кэш прогрет`);
    return { ids };
}

export const options = {
    scenarios: {
        read_mix: {
            executor: 'ramping-arrival-rate',
            startRate: 10,
            timeUnit: '1s',
            stages: [
                { duration: '30s', target: 50  },
                { duration: '1m',  target: 100 },
                { duration: '1m',  target: 100 },
                { duration: '30s', target: 0   },
            ],
            preAllocatedVUs: 50,
            maxVUs: 200,
        },
    },
    thresholds: {
        'order_read_hit':  ['p(95)<50'],
        'order_read_miss': ['p(95)<300'],
        // http_req_failed убран, так как 404 — ожидаемый ответ для "холодных" запросов
        //'http_req_failed': ['rate<0.02'],
    },
};

export default function (data) {
    if (!data.ids.length) return;

    const hot = Math.random() < 0.9;
    const id  = hot
        ? data.ids[randInt(0, data.ids.length - 1)]
        : 9_000_000 + randInt(1, 1_000_000);

    const res = http.get(
        `${BASE_URL_ORDER}/api/orders/${id}`,
        { headers: headers(1), tags: { endpoint: 'read_order', cache: hot ? 'hit' : 'miss' } }
    );

    if (res.status === 404) notFound.add(1);
    check(res, {
        '200 or 404':  (r) => r.status === 200 || r.status === 404,
        'if 200 — id': (r) => r.status !== 200 || r.json('id') !== undefined,
    });

    (hot ? hitDur : missDur).add(res.timings.duration);
}