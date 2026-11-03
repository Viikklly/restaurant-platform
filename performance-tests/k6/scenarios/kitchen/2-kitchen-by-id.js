
// Тест: GET /tickets/{id} — Redis + БД
//
// setup() САМ создаёт тикеты через order-service, затем поллингом находит их в kitchen.
// Никаких предварительных условий не нужно — просто запусти тест.
//
// Нагружает: Redis (cache-first), PostgreSQL (fallback), Jackson (сериализация)

import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL_ORDER, BASE_URL_KITCHEN } from '../../lib/config.js';
import { headers, randInt } from '../../lib/helpers.js';
import { generateSmallOrderPayload } from '../../lib/data-generator.js';

const TICKETS_NEEDED = Number(__ENV.TICKETS_NEEDED || 20);

export function setup() {
    // 1) Создаём заказы
    const createdOrderIds = [];
    for (let i = 0; i < TICKETS_NEEDED; i++) {
        const userId = randInt(1, 1000);
        const res = http.post(
            `${BASE_URL_ORDER}/api/orders`,
            JSON.stringify(generateSmallOrderPayload(userId)),
            { headers: headers(userId), tags: { endpoint: 'setup_create' } }
        );
        if (res.status === 201) {
            createdOrderIds.push(res.json('id'));
        } else {
            console.log(`setup create FAILED: status=${res.status} body=${res.body.substring(0, 200)}`);
        }
        sleep(0.05);
    }

    if (createdOrderIds.length === 0) {
        throw new Error('setup: не удалось создать ни одного заказа через order-service');
    }
    console.log(`setup: создано ${createdOrderIds.length} заказов`);

    // 2) Поллинг: ждём, пока kitchen создаст тикеты
    const createdSet = new Set(createdOrderIds);
    let tickets = [];
    const maxAttempts = 30;

    for (let attempt = 0; attempt < maxAttempts; attempt++) {
        const res = http.get(
            `${BASE_URL_KITCHEN}/api/kitchen/tickets`,
            { headers: headers(1), tags: { endpoint: 'setup_list' } }
        );

        if (res.status === 200) {
            const all = res.json();
            tickets = all.filter((t) => createdSet.has(t.orderId));
            if (tickets.length >= TICKETS_NEEDED) {
                console.log(`setup: найдено ${tickets.length} тикетов за ${attempt + 1}s`);
                break;
            }
        }
        console.log(`setup: ожидание... найдено ${tickets.length}/${TICKETS_NEEDED} (${attempt + 1}s)`);
        sleep(1);
    }

    if (tickets.length === 0) {
        throw new Error(
            `setup: kitchen не создал ни одного тикета за ${maxAttempts}s. ` +
            `Проверь Kafka: order-service → payment-service → kitchen-service.`
        );
    }

    // 3) Прогрев Redis
    for (const t of tickets) {
        http.get(
            `${BASE_URL_KITCHEN}/api/kitchen/tickets/order/${t.orderId}/items`,
            { headers: headers(1) }
        );
    }
    console.log(`setup: Redis прогрет для ${tickets.length} тикетов`);

    return { ticketIds: tickets.map((t) => t.id) };
}

export const options = {
    scenarios: {
        read_by_id: {
            executor: 'constant-vus',
            vus: 100,
            duration: '2m',
        },
    },
    thresholds: {
        'http_req_duration{endpoint:kitchen_by_id}': ['p(95)<200', 'p(99)<300'],
        'http_req_failed':                            ['rate<0.01'],
    },
};

export default function (data) {
    if (!data.ticketIds || data.ticketIds.length === 0) return;

    const id = data.ticketIds[randInt(0, data.ticketIds.length - 1)];
    const res = http.get(
        `${BASE_URL_KITCHEN}/api/kitchen/tickets/${id}`,
        { headers: headers(1), tags: { endpoint: 'kitchen_by_id' } }
    );

    check(res, {
        '200':              (r) => r.status === 200,
        'response < 50ms':  (r) => r.timings.duration < 50,
    });
}