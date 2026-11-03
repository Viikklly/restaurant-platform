
// Смешанный сценарий: login + register + validate одновременно
// Реальный профиль нагрузки на auth-service
//
// ПОСЛЕ ТЕСТА ПОЧИСТИТЬ:
// docker exec -i restaurant-postgres psql -U postgres -d authdb <<'SQL'
// DELETE FROM users WHERE email LIKE 'load_%@test.com';
// SQL

import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL_AUTH, TEST_USER, TOKEN_POOL_SIZE } from '../../lib/config.js';
import { authHeaders } from '../../lib/helpers.js';
import { generateEmail } from '../../lib/data-generator.js';

export function setup() {
    const tokens = [];
    for (let i = 0; i < TOKEN_POOL_SIZE; i++) {
        const res = http.post(
            `${BASE_URL_AUTH}/auth/login`,
            JSON.stringify(TEST_USER),
            { headers: authHeaders() }
        );
        if (res.status === 200) {
            const t = res.json('accessToken');
            if (t) tokens.push(t);
        }
        sleep(0.05);
    }

    if (tokens.length === 0) {
        throw new Error(`setup: не получили ни одного accessToken`);
    }
    console.log(`✓ Получено ${tokens.length}/${TOKEN_POOL_SIZE} токенов`);
    return { tokens };
}

export const options = {
    scenarios: {
        login_scenario: {
            executor: 'constant-vus',
            vus: 30,
            duration: '2m',
            exec: 'loginScenario',
        },
        register_scenario: {
            executor: 'constant-arrival-rate',
            rate: 20,
            timeUnit: '1s',
            duration: '2m',
            preAllocatedVUs: 50,
            maxVUs: 200,
            exec: 'registerScenario',
        },
        validate_scenario: {
            executor: 'constant-vus',
            vus: 60,
            duration: '2m',
            exec: 'validateScenario',
        },
    },
    thresholds: {
        'http_req_duration{scenario:login_scenario}':    ['p(95)<1000'],
        'http_req_duration{scenario:register_scenario}': ['p(95)<2000'],
        'http_req_duration{scenario:validate_scenario}': ['p(95)<100'],
        'http_req_failed':                                ['rate<0.01'],
    },
};

export function loginScenario() {
    const res = http.post(
        `${BASE_URL_AUTH}/auth/login`,
        JSON.stringify(TEST_USER),
        { headers: authHeaders(), tags: { endpoint: 'auth_login' } }
    );
    check(res, {
        '200':             (r) => r.status === 200,
        'has accessToken': (r) => r.json('accessToken') !== undefined,
    });
    sleep(1);
}

export function registerScenario() {
    const email = generateEmail();
    const res = http.post(
        `${BASE_URL_AUTH}/auth/register`,
        JSON.stringify({ email, username: email.split('@')[0], password: 'password123' }),
        { headers: authHeaders(), tags: { endpoint: 'auth_register' } }
    );
    check(res, { '200 or 201': (r) => r.status === 200 || r.status === 201 });
}

export function validateScenario(data) {
    const token = data.tokens[(__VU + __ITER) % data.tokens.length];
    const res = http.post(
        `${BASE_URL_AUTH}/auth/validate`,
        JSON.stringify({ token }),
        { headers: authHeaders(), tags: { endpoint: 'auth_validate' } }
    );
    check(res, {
        '200':     (r) => r.status === 200,
        'is valid': (r) => r.json('valid') === true,
    });
    sleep(0.5);
}