
// Тест: валидация JWT (gateway-сценарий)
// Нагружает: CPU (HMAC-SHA256 верификация) — очень лёгкий, но частый

import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL_AUTH, TEST_USER } from '../../lib/config.js';
import { authHeaders } from '../../lib/helpers.js';

export function setup() {
    const res = http.post(
        `${BASE_URL_AUTH}/auth/login`,
        JSON.stringify(TEST_USER),
        { headers: authHeaders() }
    );
    if (res.status !== 200) {
        throw new Error(`setup: login failed: ${res.status} ${res.body}`);
    }
    return { token: res.json('accessToken') };
}

export const options = {
    scenarios: {
        validate: {
            executor: 'constant-vus',
            vus: 100,
            duration: '1m',
        },
    },
    thresholds: {
        'http_req_duration{endpoint:auth_validate}': ['p(95)<600', 'p(99)<800'],
        'http_req_failed':                           ['rate<0.01'],
    },
};

export default function (data) {
    const res = http.post(
        `${BASE_URL_AUTH}/auth/validate`,
        JSON.stringify({ token: data.token }),
        { headers: authHeaders(), tags: { endpoint: 'auth_validate' } }
    );

    check(res, {
        '200':    (r) => r.status === 200,
        'is valid': (r) => r.json('valid') === true,
    });
}