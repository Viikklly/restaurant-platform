
// Стресс-тест: ramping arrival-rate до 500 rps
// Цель: найти точку, где order-service начинает деградировать
//
// Наблюдай:
//   docker stats --no-stream | grep order
//   curl -s localhost:8082/actuator/metrics/hikaricp.connections.active | jq
//   curl -s localhost:8082/actuator/metrics/tomcat.threads.busy | jq


import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL_ORDER } from '../../lib/config.js';
import { headers, randInt } from '../../lib/helpers.js';
import { generateSmallOrderPayload } from '../../lib/data-generator.js';

export const options = {
    scenarios: {
        stress: {
            executor: 'ramping-arrival-rate',
            startRate: 10,
            timeUnit: '1s',
            stages: [
                { duration: '30s', target: 50  },
                { duration: '1m',  target: 200 },
                { duration: '30s', target: 500 },
                { duration: '30s', target: 0   },
            ],
            preAllocatedVUs: 100,
            maxVUs: 1000,
        },
    },
    thresholds: {
        'http_req_failed': ['rate<0.10'],   // при стрессе до 10% ошибок
    },
};

export default function () {
    const userId = randInt(1, 1000);
    const res = http.post(
        `${BASE_URL_ORDER}/api/orders`,
        JSON.stringify(generateSmallOrderPayload(userId)),
        { headers: headers(userId), tags: { endpoint: 'create_order', phase: 'stress' } }
    );

    check(res, {
        '201': (r) => r.status === 201,
    });
    if (res.status !== 201) {
        console.log(`stress: status=${res.status} body=${res.body.substring(0, 200)}`);
    }
}