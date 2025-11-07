-- Script để thêm 10 xe máy điện VinFast với mã VIN thực tế
-- VinFast VIN format: LFV (manufacturer) + model code + production details
-- Các dòng xe máy điện: THEON, FELIZ, KLARA_S, KLARA_A2, TEMPEST, VENTO

-- Lưu ý: Các xe này KHÔNG được gán cho user nào (user_id = NULL)
-- Để test chức năng assign vehicle cho user sau này

INSERT INTO vehicle (
    userid,
    vin,
    vehicletype,
    batterytype,
    isactive,
    manufacturedate,
    purchasedate,
    licenseplate,
    color,
    batterycount,
    ownername
) VALUES
-- 1. VinFast Theon
(
    NULL,
    'LFVTH1A10N0000001',
    'THEON',
    'LITHIUM_ION',
    false,
    '2024-01-15',
    '2024-02-10',
    '30A1-12345',
    'Đỏ Racing',
    1,
    'Tran Dan'
),

-- 2. VinFast Feliz
(
    NULL,
    'LFVFE2B20N0000002',
    'FELIZ',
    'LITHIUM_ION',
    false,
    '2024-02-20',
    '2024-03-15',
    '29B1-67890',
    'Trắng Pearl',
    1,
    'Nguyễn Thị Hằng'
),

-- 3. VinFast Klara S
(
    NULL,
    'LFVKS3C30N0000003',
    'KLARA_S',
    'LITHIUM_ION',
    false,
    '2024-03-10',
    '2024-04-05',
    '51C1-11111',
    'Xanh Mint',
    1,
    'Lê Văn Minh'
),

-- 4. VinFast Klara A2
(
    NULL,
    'LFVKA4D40N0000004',
    'KLARA_A2',
    'LEAD_ACID',
    false,
    '2024-04-01',
    '2024-05-20',
    '30D1-22222',
    'Đen Bóng',
    1,
    'Phạm Thị Lan'
),

-- 5. VinFast Tempest
(
    NULL,
    'LFVTE5E50N0000005',
    'TEMPEST',
    'LITHIUM_ION',
    false,
    '2024-05-15',
    '2024-06-10',
    '51E1-33333',
    'Xám Titan',
    1,
    'Hoàng Văn Tuấn'
),

-- 6. VinFast Vento
(
    NULL,
    'LFVVE6F60N0000006',
    'VENTO',
    'LITHIUM_ION',
    false,
    '2024-06-20',
    '2024-07-05',
    '29F1-44444',
    'Xanh Navy',
    1,
    'Vũ Thị Mai'
),

-- 7. VinFast Theon
(
    NULL,
    'LFVTH7G70N0000007',
    'THEON',
    'LITHIUM_ION',
    false,
    '2024-07-10',
    '2024-08-01',
    '30G1-55555',
    'Đỏ Đô',
    1,
    'Đặng Văn Hùng'
),

-- 8. VinFast Feliz
(
    NULL,
    'LFVFE8H80N0000008',
    'FELIZ',
    'LITHIUM_ION',
    false,
    '2024-08-05',
    '2024-09-25',
    '51H1-66666',
    'Hồng Pastel',
    1,
    'Bùi Thị Nga'
),

-- 9. VinFast Klara S
(
    NULL,
    'LFVKS9I90N0000009',
    'KLARA_S',
    'LITHIUM_ION',
    false,
    '2024-09-20',
    '2024-10-15',
    '29I1-77777',
    'Xanh Lá',
    1,
    'Ngô Văn Long'
),

-- 10. VinFast Tempest
(
    NULL,
    'LFVTE0J00N0000010',
    'TEMPEST',
    'LITHIUM_ION',
    false,
    '2024-10-01',
    '2024-11-05',
    '30K1-88888',
    'Bạc Metallic',
    1,
    'Đinh Thị Hương'
);

-- Query để kiểm tra kết quả
-- SELECT v.vehicleid, v.vin, v.vehicletype, v.licenseplate, v.color, v.isactive, v.userid, v.ownername
-- FROM vehicle v
-- WHERE v.vin LIKE 'LFV%'
-- ORDER BY v.vehicleid DESC
-- LIMIT 10;

-- Query để kiểm tra xe chưa được gán cho user nào
-- SELECT COUNT(*) as total_unassigned_vehicles
-- FROM vehicle
-- WHERE userid IS NULL AND vin LIKE 'LFV%';


