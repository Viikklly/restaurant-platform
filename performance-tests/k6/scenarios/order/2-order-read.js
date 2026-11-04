// Тест: чтение заказов, горячий vs холодный кэш
// Доказывает эффективность @Cacheable("orders")
//
// setup():
//   - создаёт 200 заказов
//   - первые 100 прогревает в Redis (GET → @Cacheable → Redis)
//   - вторые 100 оставляет в БД, но НЕ в Redis (cold path)
//
// Тест:
//   - 90% запросов → hot  ID (Redis hit, ~2-5ms)
//   - 10% запросов → cold ID (PostgreSQL miss → Redis, ~5-15ms)
//
// Ожидание:
//   order_read_hit  p95 < 50ms
//   order_read_miss p95 < 300ms
//   http_req_failed 0% (нет 404)

import http from 'k6/http';
import { check } from 'k6';
import { Trend, Counter } from 'k6/metrics';
import { BASE_URL_ORDER } from '../../lib/config.js';
import { headers, randInt } from '../../lib/helpers.js';
import { generateSmallOrderPayload } from '../../lib/data-generator.js';

const hitDur   = new Trend('order_read_hit',  true);
const missDur  = new Trend('order_read_miss', true);
const notFound = new Counter('order_read_404');  // оставляем для диагностики (ожидаем 0)

export function setup() {
    const hotIds  = [];
    const coldIds = [];
    const userId  = 1;

    // 1. Создаём 200 заказов
    for (let i = 0; i < 200; i++) {
        const res = http.post(
            `${BASE_URL_ORDER}/api/orders`,
            JSON.stringify(generateSmallOrderPayload(userId)),
            { headers: headers(userId) }
        );
        if (res.status === 201) {
            const id = res.json('id');
            if (i < 100) {
                hotIds.push(id);
            } else {
                coldIds.push(id);
            }
        }
    }

    // 2. Прогреваем Redis только для первых 100 (hot)
    for (const id of hotIds) {
        http.get(`${BASE_URL_ORDER}/api/orders/${id}`, { headers: headers(userId) });
    }

    console.log(`setup: ${hotIds.length} hot (в Redis) + ${coldIds.length} cold (только в БД)`);
    return { hotIds, coldIds };
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
            preAllocatedVUs: 100,
            maxVUs: 500,  // ← было 200, стало 500 (достичь 100 RPS на пике)
        },
    },
    thresholds: {
        'order_read_hit':  ['p(95)<50'],
        'order_read_miss': ['p(95)<300'],
        // http_req_failed должен быть 0% (нет 404, все ID существуют)
        'http_req_failed': ['rate<0.01'],
    },
};

export default function (data) {
    if (!data.hotIds.length || !data.coldIds.length) return;

    const hot = Math.random() < 0.9;
    const id  = hot
        ? data.hotIds[randInt(0, data.hotIds.length - 1)]      // Redis hit
        : data.coldIds[randInt(0, data.coldIds.length - 1)];   // PostgreSQL miss

    const res = http.get(
        `${BASE_URL_ORDER}/api/orders/${id}`,
        { headers: headers(1), tags: { endpoint: 'read_order', cache: hot ? 'hit' : 'miss' } }
    );

    if (res.status === 404) notFound.add(1);  // ожидаем 0 (все ID существуют)

    check(res, {
        '200':         (r) => r.status === 200,
        'if 200 — id': (r) => r.status !== 200 || r.json('id') !== undefined,
    });

    (hot ? hitDur : missDur).add(res.timings.duration);
}