-- Script để drop 2 tables: bookingbatteryitem và bookingvehicleitem
-- Chạy script này trong DBeaver, pgAdmin hoặc bất kỳ PostgreSQL client nào

-- Drop table bookingbatteryitem
DROP TABLE IF EXISTS bookingbatteryitem CASCADE;

-- Drop table bookingvehicleitem
DROP TABLE IF EXISTS bookingvehicleitem CASCADE;

-- Kiểm tra các table đã được xóa
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
AND table_name IN ('bookingbatteryitem', 'bookingvehicleitem');
