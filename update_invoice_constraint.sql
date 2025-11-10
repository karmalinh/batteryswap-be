-- Script để cập nhật constraint cho Invoice.InvoiceType
-- Các giá trị: BOOKING, SUBSCRIPTION, WALLET_TOPUP, PENALTY

-- 1. Xóa constraint cũ (nếu có)
ALTER TABLE invoice DROP CONSTRAINT IF EXISTS invoice_invoicetype_check;

-- 2. Cập nhật các giá trị REFUND (nếu có) thành BOOKING trước khi thêm constraint
UPDATE invoice
SET invoicetype = 'BOOKING'
WHERE invoicetype = 'REFUND';

-- 3. Kiểm tra xem còn giá trị nào không hợp lệ không
SELECT DISTINCT invoicetype
FROM invoice
WHERE invoicetype NOT IN ('BOOKING', 'SUBSCRIPTION', 'WALLET_TOPUP', 'PENALTY');

-- 4. Thêm constraint mới với đầy đủ các giá trị
ALTER TABLE invoice
ADD CONSTRAINT invoice_invoicetype_check
CHECK (invoicetype IN ('BOOKING', 'SUBSCRIPTION', 'WALLET_TOPUP', 'PENALTY'));

-- 5. Kiểm tra constraint đã được thêm thành công
SELECT constraint_name, check_clause
FROM information_schema.check_constraints
WHERE constraint_name = 'invoice_invoicetype_check';

