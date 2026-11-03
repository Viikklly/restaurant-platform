
// Smoke: GET /auth/test — простой открытый эндпоинт
// Используется для быстрой проверки, что auth-service жив и отвечает < 200ms

import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL_AUTH, TEST_USER } from '../../lib/config.js';
import { authHeaders, bearerHeaders } from '../../lib/helpers.js';

export function setup() {
    const res = http.post(
        `${BASE_URL_AUTH}/auth/login`,
        JSON.stringify(TEST_USER),
        { headers: authHeaders() }
    );
    return { token: res.json('accessToken') };
}

export const options = {
    stages: [
        { duration: '30s', target: 20 },
        { duration: '1m',  target: 50 },
        { duration: '30s', target: 0  },
    ],
    thresholds: {
        'http_req_duration{endpoint:auth_test}': ['p(95)<500', 'p(99)<1000'],
        'http_req_failed':                       ['rate<0.01'],
    },
};

export default function (data) {
    const res = http.get(
        `${BASE_URL_AUTH}/auth/test`,
        { headers: bearerHeaders(data.token), tags: { endpoint: 'auth_test' } }
    );

    check(res, {
        '200':              (r) => r.status === 200,
        'response < 200ms': (r) => r.timings.duration < 200,
    });

    sleep(Math.random() * 2 + 1);   // think time 1–3s
}