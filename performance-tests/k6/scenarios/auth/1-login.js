
// Тест: реальная авторизация (BCrypt + JWT)
// Нагружает: CPU (BCrypt ~100ms), PostgreSQL (SELECT), JWT (HMAC)

import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL_AUTH, TEST_USER } from '../../lib/config.js';
import { authHeaders } from '../../lib/helpers.js';

export const options = {
    stages: [
        { duration: '30s', target: 20 },
        { duration: '1m',  target: 50 },
        { duration: '30s', target: 0  },
    ],
    thresholds: {
        'http_req_duration{endpoint:auth_login}': ['p(95)<1000', 'p(99)<2000'],
        'http_req_failed':                        ['rate<0.01'],
    },
};

export default function () {
    const res = http.post(
        `${BASE_URL_AUTH}/auth/login`,
        JSON.stringify(TEST_USER),
        { headers: authHeaders(), tags: { endpoint: 'auth_login' } }
    );

    check(res, {
        '200': (r) => r.status === 200,
        'has accessToken': (r) => r.json('accessToken') !== undefined,
    });
}