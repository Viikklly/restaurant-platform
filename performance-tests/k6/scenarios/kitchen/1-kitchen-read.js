
// Smoke: все GET-эндпоинты kitchen живы
// Нагружает: Tomcat, PostgreSQL (SELECT), Jackson

import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL_KITCHEN } from '../../lib/config.js';
import { headers, pick } from '../../lib/helpers.js';

export const options = {
    stages: [
        { duration: '30s', target: 20 },
        { duration: '1m',  target: 50 },
        { duration: '30s', target: 0  },
    ],
    thresholds: {
        'http_req_duration{endpoint:kitchen_list}': ['p(95)<500', 'p(99)<1000'],
        'http_req_failed':                          ['rate<0.01'],
    },
};

export default function () {
    const paths = [
        '/api/kitchen/tickets',
        '/api/kitchen/tickets/status/open',
        '/api/kitchen/tickets/status/in-progress',
        '/api/kitchen/tickets/status/ready',
    ];
    const path = pick(paths);

    const res = http.get(
        `${BASE_URL_KITCHEN}${path}`,
        { headers: headers(1), tags: { endpoint: 'kitchen_list' } }
    );

    check(res, {
        '200':            (r) => r.status === 200,
        'is JSON array':  (r) => r.headers['Content-Type']?.includes('application/json'),
    });

    sleep(0.5);
}