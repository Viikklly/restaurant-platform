-- Удаляем в правильном порядке (с учетом внешних ключей)
TRUNCATE TABLE order_items CASCADE;
TRUNCATE TABLE orders CASCADE;
TRUNCATE TABLE items CASCADE;

-- Сбрасываем последовательности
ALTER SEQUENCE IF EXISTS orders_id_seq RESTART WITH 1;
ALTER SEQUENCE IF EXISTS items_id_seq RESTART WITH 1;