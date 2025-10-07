-- Script đơn giản với VIN chuẩn và dễ debug
DELETE FROM Vehicle WHERE VehicleId > 0 OR VehicleId = 0;

-- Thêm cột BatteryCount nếu chưa có
ALTER TABLE Vehicle ADD COLUMN IF NOT EXISTS BatteryCount INTEGER DEFAULT 1;

-- Reset sequence
ALTER SEQUENCE vehicle_vehicleid_seq RESTART WITH 1;

-- Chỉ thêm vài xe test với VIN đơn giản
INSERT INTO Vehicle (VIN, vehicleType, batteryType, isActive, ManufactureDate, PurchaseDate, LicensePlate, Color, BatteryCount) VALUES
('12345678901234567', 'THEON', 'LITHIUM_ION', false, '2023-01-15', '2023-02-01', '29A1-12345', 'Đỏ', 1),
('12345678901234568', 'FELIZ', 'LITHIUM_ION', false, '2023-02-10', '2023-02-25', '29A1-12346', 'Xanh', 1),
('12345678901234569', 'KLARA_S', 'LITHIUM_ION', false, '2023-03-05', '2023-03-20', '29A1-12347', 'Trắng', 2);
