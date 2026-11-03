
export const BASE_URL_AUTH    = __ENV.BASE_URL_AUTH    || 'http://auth-service:8081';
export const BASE_URL_ORDER   = __ENV.BASE_URL_ORDER   || 'http://order-service:8082';
export const BASE_URL_KITCHEN = __ENV.BASE_URL_KITCHEN || 'http://kitchen-service:8083';
export const BASE_URL_PAYMENT = __ENV.BASE_URL_PAYMENT || 'http://payment-service:8084';

// Меню из OrderServiceApplication.initDatabase()
export const MENU_AVAILABLE = ['Пицца', 'Паста'];

// application.yml payment-service: payment.max-amount=10000
export const PAYMENT_MAX_AMOUNT = 10000;

// Статусы kitchen
export const KITCHEN_STATUSES = ['open', 'in-progress', 'ready'];

// Тестовый пользователь для auth (уже создан в БД)
export const TEST_USER = {
    email: 'bobi@test.com',
    password: 'password123',
};

// Пул для setup(): сколько токенов готовим
export const TOKEN_POOL_SIZE = Number(__ENV.TOKEN_POOL_SIZE || 50);