# HƯỚNG DẪN CẬP NHẬT INVOICE VÀ BOOKING

## Tóm tắt các thay đổi

### 1. Invoice Entity (Hóa đơn)
**Các cột mới:**
- `pricePerSwap` (Double): Giá mỗi lần đổi pin (mặc định: 15,000 VNĐ)
- `numberOfSwaps` (Integer): Số lần đổi pin
- `totalAmount` = `pricePerSwap` × `numberOfSwaps` (tự động tính)

**Phương thức mới:**
- `calculateTotalAmount()`: Tính tổng tiền tự động

### 2. Booking Entity (Đặt chỗ)
**Các cột mới:**
- `vehicleType` (String): Loại xe (lấy từ Vehicle, không cần join mỗi lần query)
- `amount` (Double): Giá tiền cho booking này (mặc định: 15,000 VNĐ)

### 3. BookingService
**Cập nhật:**
- Tự động lấy `vehicleType` từ Vehicle khi tạo booking
- Tự động set `amount` = 15,000 VNĐ cho mỗi booking

### 4. InvoiceService
**Phương thức mới/cập nhật:**
- `createInvoice()`: Tự động tính `numberOfSwaps` và `totalAmount`
- `updateInvoice()`: Cập nhật và tính lại tổng tiền
- `addBookingToInvoice()`: Thêm booking vào invoice và cập nhật tổng tiền
- `getInvoiceById()`: Lấy invoice theo ID

### 5. DTOs
**InvoiceResponseDTO:**
- Thêm `pricePerSwap` và `numberOfSwaps`

**BookingInfoDTO:**
- Thêm `vehicleType` và `amount`

---

## BƯỚC 1: Chạy SQL Script để cập nhật database

### Cách 1: Sử dụng DataGrip/Database Tool
1. Mở file `update_invoice_booking_schema.sql`
2. Kết nối đến database Railway của bạn
3. Chạy toàn bộ script

### Cách 2: Chạy từng đoạn SQL

```sql
-- 1. Thêm cột mới cho Invoice
ALTER TABLE Invoice 
ADD COLUMN IF NOT EXISTS priceperswap DOUBLE PRECISION DEFAULT 15000.0,
ADD COLUMN IF NOT EXISTS numberofswaps INTEGER DEFAULT 0;

-- 2. Thêm cột mới cho Booking
ALTER TABLE Booking 
ADD COLUMN IF NOT EXISTS vehicletype VARCHAR(50),
ADD COLUMN IF NOT EXISTS amount DOUBLE PRECISION;

-- 3. Cập nhật dữ liệu cho các invoice hiện có
UPDATE Invoice 
SET priceperswap = 15000.0 
WHERE priceperswap IS NULL;

UPDATE Invoice 
SET numberofswaps = (
    SELECT COUNT(*) 
    FROM Booking 
    WHERE Booking.InvoiceId = Invoice.invoiceid
)
WHERE numberofswaps = 0 OR numberofswaps IS NULL;

UPDATE Invoice 
SET totalamount = priceperswap * numberofswaps
WHERE priceperswap IS NOT NULL AND numberofswaps IS NOT NULL;

-- 4. Cập nhật dữ liệu cho các booking hiện có
UPDATE Booking b
SET vehicletype = v.vehicletype::VARCHAR
FROM Vehicle v
WHERE b.VehicleId = v.VehicleId
AND b.vehicletype IS NULL;

UPDATE Booking 
SET amount = 15000.0 
WHERE amount IS NULL;

-- 5. Tạo index
CREATE INDEX IF NOT EXISTS idx_booking_vehicletype ON Booking(vehicletype);
CREATE INDEX IF NOT EXISTS idx_booking_amount ON Booking(amount);
CREATE INDEX IF NOT EXISTS idx_invoice_numberofswaps ON Invoice(numberofswaps);
```

---

## BƯỚC 2: Khởi động lại ứng dụng

```bash
# Dừng ứng dụng nếu đang chạy (Ctrl+C)
# Sau đó khởi động lại
mvn spring-boot:run
```

Hoặc trong IntelliJ IDEA: Click vào nút Run/Debug

---

## BƯỚC 3: Kiểm tra API

### 1. Tạo Booking mới
```
POST /api/bookings
{
  "vehicleId": 1,
  "stationId": 1,
  "bookingDate": "2025-10-17",
  "timeSlot": "10:00"
}
```

**Kết quả mong đợi:**
- Booking được tạo với `vehicleType` và `amount` tự động

### 2. Xem chi tiết Invoice
```
GET /api/invoices/{invoiceId}
```

**Kết quả mong đợi:**
```json
{
  "id": 10000,
  "createdDate": "2025-10-15T21:00:00",
  "totalAmount": 45000.0,
  "pricePerSwap": 15000.0,
  "numberOfSwaps": 3,
  "bookings": [
    {
      "bookingId": 1,
      "bookingDate": "2025-10-16",
      "timeSlot": "10:00:00",
      "vehicleType": "THEON",
      "amount": 15000.0
    },
    // ...
  ]
}
```

---

## BƯỚC 4: Tạo dữ liệu mẫu Invoice (Tùy chọn)

```sql
-- Tạo invoice mẫu
INSERT INTO Invoice (createddate, totalamount, priceperswap, numberofswaps)
VALUES 
(NOW(), 45000.0, 15000.0, 3),
(NOW(), 30000.0, 15000.0, 2),
(NOW(), 15000.0, 15000.0, 1);

-- Liên kết booking với invoice
UPDATE Booking 
SET InvoiceId = (SELECT invoiceid FROM Invoice ORDER BY invoiceid DESC LIMIT 1)
WHERE BookingId IN (1, 2, 3);
```

---

## LƯU Ý QUAN TRỌNG

### 1. Về giá tiền (Price)
- Giá mặc định: **15,000 VNĐ** cho mỗi lần đổi pin
- Có thể thay đổi giá cho từng invoice nếu cần

### 2. Về vehicleType
- Tự động lấy từ Vehicle khi tạo booking
- Không cần join với bảng Vehicle khi query
- Giúp tăng hiệu suất truy vấn

### 3. Về amount trong Booking
- Mỗi booking có giá riêng (mặc định 15,000)
- Tổng tiền invoice = tổng amount của các booking

### 4. Về numberOfSwaps
- Tự động tính dựa trên số lượng booking liên kết với invoice
- Có thể cập nhật thủ công nếu cần

---

## Kiểm tra kết quả

### Query kiểm tra Invoice:
```sql
SELECT 
    invoiceid, 
    createddate, 
    priceperswap, 
    numberofswaps, 
    totalamount,
    (priceperswap * numberofswaps) as calculated_total
FROM Invoice
ORDER BY invoiceid;
```

### Query kiểm tra Booking:
```sql
SELECT 
    BookingId,
    UserId,
    VehicleId,
    vehicletype,
    amount,
    bookingdate,
    timeslot,
    bookingstatus,
    InvoiceId
FROM Booking
ORDER BY BookingId;
```

### Query kiểm tra Invoice với Bookings:
```sql
SELECT 
    i.invoiceid,
    i.priceperswap,
    i.numberofswaps,
    i.totalamount,
    COUNT(b.BookingId) as actual_booking_count,
    SUM(b.amount) as total_booking_amount
FROM Invoice i
LEFT JOIN Booking b ON b.InvoiceId = i.invoiceid
GROUP BY i.invoiceid, i.priceperswap, i.numberofswaps, i.totalamount
ORDER BY i.invoiceid;
```

---

## Xử lý lỗi thường gặp

### Lỗi: "Cannot resolve column"
**Nguyên nhân:** Cột chưa được tạo trong database
**Giải pháp:** Chạy lại SQL script ở BƯỚC 1

### Lỗi: "vehicletype is null"
**Nguyên nhân:** Vehicle không có vehicleType
**Giải pháp:** 
```sql
-- Cập nhật vehicleType cho Vehicle
UPDATE Vehicle 
SET vehicletype = 'THEON' 
WHERE vehicletype IS NULL;
```

### Lỗi khi tạo booking: "vehicle.getVehicleType() is null"
**Nguyên nhân:** Vehicle không có vehicleType được set
**Giải pháp:** Đảm bảo Vehicle có vehicleType trước khi tạo booking

---

## API Endpoints mới/cập nhật

### Invoice APIs:
- `GET /api/invoices` - Lấy tất cả invoices
- `GET /api/invoices/{id}` - Lấy chi tiết invoice (bao gồm pricePerSwap, numberOfSwaps)
- `POST /api/invoices` - Tạo invoice mới (tự động tính totalAmount)
- `PUT /api/invoices/{id}` - Cập nhật invoice (tự động tính lại totalAmount)

### Booking APIs:
- `POST /api/bookings` - Tạo booking (tự động set vehicleType và amount)
- Các API khác giữ nguyên

---

## Tóm tắt công thức tính

```
Invoice:
  pricePerSwap = 15,000 VNĐ (mặc định)
  numberOfSwaps = COUNT(bookings)
  totalAmount = pricePerSwap × numberOfSwaps

Booking:
  vehicleType = lấy từ Vehicle
  amount = 15,000 VNĐ (mặc định)
```

---

## Hoàn tất ✅

Sau khi hoàn thành các bước trên, hệ thống của bạn sẽ:
1. ✅ Tự động tính giá invoice dựa trên số lần đổi pin
2. ✅ Lưu loại xe vào booking để query nhanh hơn
3. ✅ Quản lý giá tiền chi tiết cho từng booking
4. ✅ Chuẩn hóa cấu trúc thanh toán

---

**Nếu có vấn đề, hãy kiểm tra:**
1. Database đã được cập nhật chưa?
2. Application có khởi động lại chưa?
3. Dữ liệu test có đúng format không?

