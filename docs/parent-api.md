# API phụ huynh: học sinh, điểm và thời khóa biểu

Nhánh yêu cầu 2, dùng Spring Boot 4.2.0 và Java 25 theo `backend/pom.xml`.
Mọi endpoint yêu cầu session đăng nhập với role `PARENT`. `parentId` lấy từ
principal của session; client không truyền quyền sở hữu.

| Endpoint | Query tùy chọn | `data` |
| --- | --- | --- |
| `GET /api/students` | Không | `{items:[{id,studentCode,fullName,dateOfBirth,class:{id,name,classCode,schoolYear}}]}` |
| `GET /api/students/:id/grades` | `schoolYear`, `term`, `subjectId` | `{studentId,items:[{id,subject:{id,name},scoreType,score,term,schoolYear}]}` |
| `GET /api/students/:id/timetable` | `schoolYear`, `term` | `{studentId,items:[{weekday,period,subject:{id,name},room,schoolYear,term}]}` |

ID trả về là string; ID đầu vào phải là số nguyên dương trong miền SQLite INTEGER.
`schoolYear` có dạng `YYYY-YYYY`, `term` là `HK1` hoặc `HK2`. Bỏ query để lấy
tất cả; query được gửi nhưng rỗng hoặc sai định dạng trả 400 `VALIDATION_ERROR`.
Không lấy năm học điểm/lịch mặc định từ khóa học của lớp.

Danh sách chỉ chứa con đã liên kết, gồm cả quan hệ `GUARDIAN`. Hai truy vấn chi
tiết JOIN `parent_students` và ràng buộc cả parent và student ngay trong SQL.
Học sinh ngoài liên kết và ID không tồn tại đều trả cùng 404 `NOT_FOUND`.
Học sinh có liên kết nhưng chưa có dữ liệu khớp bộ lọc trả 200 với `items:[]`.
Sai vai trò trả 403, chưa đăng nhập trả 401 theo envelope chung.

Kiểm thử: tại thư mục `backend`, dùng JDK 25 chạy `./mvnw test`
(Windows: `.\mvnw.cmd test`). `StudentApiTests` sử dụng SQLite in-memory riêng,
kiểm tra session đăng nhập thật, ba vai trò, IDOR, mất liên kết, dữ liệu rỗng,
bộ lọc, validation và cấu trúc response.
