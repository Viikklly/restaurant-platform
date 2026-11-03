import { MENU_AVAILABLE } from './config.js';
import { pick, randInt } from './helpers.js';

// Обычный заказ: 1–3 позиции, quantity 1–2
// Сумма максимум ~3000 < 10000 → оплата SUCCESS
export function generateOrderPayload(userId = 1) {
    const n = randInt(1, 3);
    const items = Array.from({ length: n }, () => ({
        productName: pick(MENU_AVAILABLE),
        quantity: randInt(1, 2),
    }));
    return { userId, items };
}

// Минимальный заказ (для E2E — быстрее готовится)
export function generateSmallOrderPayload(userId = 1) {
    return {
        userId,
        items: [{ productName: pick(MENU_AVAILABLE), quantity: 1 }],
    };
}

// Заказ, который заведомо провалит оплату: 21 × 499.99 = 10499.79 > 10000
export function generateHugeOrderPayload(userId = 1) {
    return {
        userId,
        items: [{ productName: 'Пицца', quantity: 21 }],
    };
}

// Невалидный заказ: несуществующее блюдо → 500
export function generateInvalidItemPayload(userId = 1) {
    return {
        userId,
        items: [{ productName: 'Суши', quantity: 1 }],
    };
}

// Пустой заказ → 400 (@Valid @NotEmpty)
export function generateEmptyOrderPayload(userId = 1) {
    return { userId, items: [] };
}

// Случайный email для register
export function generateEmail() {
    const uuid = 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
        const r = (Math.random() * 16) | 0;
        const v = c === 'x' ? r : (r & 0x3) | 0x8;
        return v.toString(16);
    });
    return `load_${uuid}@test.com`;
}