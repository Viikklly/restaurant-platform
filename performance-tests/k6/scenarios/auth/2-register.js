
// Тест: регистрация новых пользователей
// Нагружает: INSERT в users, BCrypt (hash), JWT (sign)
//
// ПОСЛЕ ТЕСТА ПОЧИСТИТЬ:
// docker exec -i restaurant-postgres psql -U postgres -d authdb <<'SQL'
// DELETE FROM users WHERE email LIKE 'load_%@test.com';
// SQL

import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL_AUTH } from '../../lib/config.js';
import { authHeaders } from '../../lib/helpers.js';
import { generateEmail } from '../../lib/data-generator.js';

export const options = {
    vus: 20,
    duration: '1m',
    thresholds: {
        'http_req_duration{endpoint:auth_register}': ['p(95)<1500'],
        'http_req_failed':                           ['rate<0.01'],
    },
};

export default function () {
    const email = generateEmail();
    const res = http.post(
        `${BASE_URL_AUTH}/auth/register`,
        JSON.stringify({
            email,
            username: email.split('@')[0],
            password: 'password123',
        }),
        { headers: authHeaders(), tags: { endpoint: 'auth_register' } }
    );

    check(res, {
        '200 or 201': (r) => r.status === 200 || r.status === 201,
        'has accessToken': (r) => r.json('accessToken') !== undefined,
    });
}