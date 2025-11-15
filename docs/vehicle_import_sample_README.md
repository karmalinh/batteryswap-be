Hướng dẫn file CSV mẫu để import Vehicle

Vị trí: src/main/resources/vehicle_import_sample.csv

Mô tả cột (header):
- vin: Mã VIN của xe (bắt buộc, chuỗi không trùng)
- vehicletype: Loại xe (THEON, FELIZ, KLARA_S, KLARA_A2, TEMPEST, VENTO, ...)
- batterytype: Loại pin (LITHIUM_ION, LEAD_ACID, ...)
- ownername: Tên chủ xe (không bắt buộc)
- licenseplate: Biển số (không bắt buộc nhưng nếu có thì không được trùng)
- color: Màu xe (không bắt buộc)
- batterycount: Số pin trên xe (bắt buộc >= 1)
- manufacturedate: Ngày sản xuất (định dạng chấp nhận: YYYY-MM-DD, YYYY/MM/DD, DD/MM/YYYY, DD-MM-YYYY, d/M/yyyy, d-M-yyyy)
- purchasedate: Ngày mua (các định dạng giống manufacturedate)
- battery_ids: (tuỳ chọn) danh sách BatteryId đã có trong hệ thống, phân cách bằng dấu phẩy hoặc chấm phẩy. Nếu cung cấp, các id này sẽ được liên kết vào bảng VehicleBattery.

Lưu ý:
- Nếu ở cột `ownername` để trống thì xe sẽ không được gán user (userId = null).
- Nếu cung cấp `battery_ids`, hệ thống sẽ kiểm tra tồn tại từng BatteryId trong bảng Battery; nếu không tồn tại sẽ báo lỗi cho dòng đó.
- `batterycount` nếu khác với số lượng `battery_ids` sẽ bị coi là lỗi.

Ví dụ dòng:
VFAB123456789,THEON,LITHIUM_ION,Nguyen Van A,29A-12345,Red,1,2020-01-01,2023-02-19,BAT001

Gợi ý test:
- Dùng tài khoản staff để gọi API import với file này.
- Kiểm tra bảng `vehicle` và `vehiclebattery` sau khi import.

