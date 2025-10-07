-- Đánh số thứ tự cho 7 vehicleId bất kỳ
WITH vehicles AS (
    SELECT vehicleId, ROW_NUMBER() OVER (ORDER BY vehicleId) AS rn
    FROM Vehicle
    ORDER BY vehicleId
    LIMIT 7
),
-- Đánh số thứ tự cho 7 userId bắt đầu bằng 'DR'
     users AS (
         SELECT userId, ROW_NUMBER() OVER (ORDER BY userId) AS rn
         FROM users
         WHERE userId LIKE 'DR%'
         ORDER BY userId
         LIMIT 7
     ),
-- Ghép từng vehicleId với từng userId theo số thứ tự
     mapping AS (
         SELECT v.vehicleId, u.userId
         FROM vehicles v
                  JOIN users u ON v.rn = u.rn
     )
-- Gán userId cho vehicleId
UPDATE Vehicle
SET userId = m.userId
FROM mapping m
WHERE Vehicle.vehicleId = m.vehicleId;



-- Kiểm tra dữ liệu đã thêm
SELECT * FROM vehicle WHERE userid = 'DR001' ORDER BY vehicleid;