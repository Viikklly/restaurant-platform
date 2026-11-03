
import { check } from 'k6';

function randomHex(n) {
    let s = '';
    for (let i = 0; i < n; i++) s += Math.floor(Math.random() * 16).toString(16);
    return s;
}

// Заголовки, которые нужны ВСЕМ сервисам
export function headers(userId = 1, extraTags = {}) {
    // __ITER не существует внутри setup(), поэтому используем безопасный доступ
    const iter = typeof __ITER !== 'undefined' ? __ITER : 0;

    return {
        'Content-Type': 'application/json',
        'X-User-Id': String(userId),
        'X-Correlation-Id': `k6-${__VU}-${iter}-${Date.now()}`,
        'X-B3-TraceId': randomHex(32),
        'X-B3-SpanId':  randomHex(16),
        'X-B3-Sampled': '1',
        ...extraTags,
    };
}

// Только для auth (там нет X-User-Id)
export function authHeaders() {
    return { 'Content-Type': 'application/json' };
}

// Только для auth, когда есть Bearer
export function bearerHeaders(token) {
    return {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
    };
}

export function pick(arr) {
    return arr[Math.floor(Math.random() * arr.length)];
}

export function randInt(min, max) {
    return Math.floor(Math.random() * (max - min + 1)) + min;
}

export function checkStatus(res, expected) {
    return check(res, { [`status ${expected}`]: (r) => r.status === expected });
}