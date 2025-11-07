-- Script để thêm 1 triệu VND vào wallet của user DR018
-- Ngày tạo: 2025-11-06

-- Cập nhật wallet cho user DR018
UPDATE "users"
SET walletbalance = walletbalance + 1000000
WHERE userid = 'DR018';

-- Query để kiểm tra kết quả
SELECT userid, fullname, walletbalance, email
FROM "users"
WHERE userid = 'DR018';

-- Query để xem constraint giới hạn wallet (nếu có)
-- SELECT conname, pg_get_constraintdef(oid)
-- FROM pg_constraint
-- WHERE conrelid = '"user"'::regclass
-- AND contype = 'c';

