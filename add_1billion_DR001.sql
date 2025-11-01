-- Query thêm 1 tỉ VNĐ cho user DR001
-- Date: 2025-11-01

-- ========================================
-- 1. KIỂM TRA TRẠNG THÁI USER DR001 TRƯỚC KHI THÊM TIỀN
-- ========================================

-- Kiểm tra thông tin hiện tại của user DR001
SELECT
    userid,
    fullname,
    walletbalance,
    roleid,
    isverified,
    createat,
    updateat
FROM users
WHERE userid = 'DR004';

-- ========================================
-- 2. THÊM 1 TỈ VNĐ CHO USER DR001
-- ========================================

-- Cộng thêm 1,000,000,000 VNĐ (1 tỉ) vào wallet của user DR001
UPDATE users
SET walletbalance = COALESCE(walletbalance, 0) + 1000000000,
    updateat = CURRENT_TIMESTAMP
WHERE userid = 'DR004';

-- ========================================
-- 3. KIỂM TRA KẾT QUẢ SAU KHI CẬP NHẬT
-- ========================================

-- Xác nhận cập nhật thành công
SELECT
    userid,
    fullname,
    walletbalance,
    CASE
        WHEN walletbalance >= 1000000000 THEN 'HIGH BALANCE (1B+)'
        WHEN walletbalance >= 100000000 THEN 'MEDIUM BALANCE (100M+)'
        ELSE 'NORMAL BALANCE'
    END as balance_status,
    updateat
FROM users
WHERE userid = 'DR004';

-- ========================================
-- 4. KIỂM TRA TỔNG QUAN WALLET TRONG HỆ THỐNG
-- ========================================

-- Thống kê wallet balance tổng quan
SELECT
    COUNT(*) as total_users,
    COUNT(CASE WHEN walletbalance > 0 THEN 1 END) as users_with_balance,
    COUNT(CASE WHEN walletbalance >= 1000000000 THEN 1 END) as users_over_1b,
    AVG(walletbalance) as avg_balance,
    MAX(walletbalance) as max_balance,
    MIN(walletbalance) as min_balance
FROM users;

-- Top 10 users có wallet balance cao nhất
SELECT
    userid,
    fullname,
    walletbalance,
    CASE
        WHEN walletbalance > 10000000000 THEN 'CRITICAL OVERFLOW'
        WHEN walletbalance > 2000000000 THEN 'SUSPICIOUS HIGH'
        WHEN walletbalance >= 1000000000 THEN 'HIGH BALANCE'
        ELSE 'NORMAL'
    END as balance_status
FROM users
WHERE walletbalance > 0
ORDER BY walletbalance DESC
LIMIT 10;

-- ========================================
-- 5. TẠO LOG GIAO DỊCH (TÙY CHỌN)
-- ========================================

-- Nếu có bảng transaction_logs, có thể tạo record
/*
INSERT INTO transaction_logs (
    userid,
    amount,
    transaction_type,
    description,
    created_at
)
VALUES (
    'DR001',
    1000000000,
    'ADMIN_ADD',
    'Admin manually added 1,000,000,000 VND (1 billion) to wallet',
    CURRENT_TIMESTAMP
);
*/
