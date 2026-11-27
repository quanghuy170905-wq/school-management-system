# API giáo viên: lớp, học sinh, môn và nhập điểm

Nhánh yêu cầu 3, dùng Spring Boot 4.2.0 và Java 25 theo `backend/pom.xml`.
Mọi endpoint yêu cầu session; `teacherId` và người cập nhật lấy từ principal.

| Endpoint | Quyền và dữ liệu |
| --- | --- |
| `GET /api/teacher/classes` | TEACHER; `{items:[{classId,className,subjectId,subjectName,isHomeroom}]}`. Chỉ môn được phân công; lớp chỉ chủ nhiệm có hai field môn là null. |
| `GET /api/classes/:id/students` | TEACHER là GVCN hoặc có phân công tại lớp; `{items:[{id,fullName,studentCode}]}`. |
| `GET /api/subjects` | TEACHER hoặc ADMIN; `{items:[{id,name,subjectCode}]}`. PARENT không được truy cập. |
| `GET /api/classes/:id/grades?subjectId=` | TEACHER đúng phân công lớp/môn; lọc thêm `schoolYear`, `term`, `scoreType` tùy chọn. |
| `PUT /api/grades` | TEACHER đúng phân công; học sinh phải thuộc lớp. Một ô điểm cho mỗi request. |

GET điểm trả `{classId,subjectId,items:[{studentId,studentName,grades:[{id,scoreType,score,term,schoolYear}]}]}`.
Mọi học sinh trong lớp được giữ lại, kể cả chưa có điểm khớp bộ lọc (`grades:[]`).
Lớp được phân công nhưng chưa có học sinh trả `items:[]`. Bỏ bộ lọc nghĩa là
lấy tất cả; không tự chọn năm từ khóa học của lớp. `term`/`schoolYear` trong mỗi
ô giúp phân biệt dữ liệu khi lấy nhiều năm/kỳ.

PUT nhận đúng các field:

```json
{
  "classId": "1",
  "subjectId": "1",
  "studentId": "1",
  "scoreType": "TX1",
  "term": "HK1",
  "schoolYear": "2024-2025",
  "score": 8.25
}
```

Gửi `Content-Type: application/json` và `Origin` nằm trong allowlist.
Điểm từ 0 đến 10, tối đa 2 chữ số thập phân; không gửi null để xóa điểm.
ID phải là số nguyên dương trong miền SQLite INTEGER và được trả về dưới dạng
string. Năm học có dạng `YYYY-YYYY`; kỳ chỉ `HK1`/`HK2`; loại điểm chỉ
`TX1`/`TX2`/`GK`/`CK`. Field ngoài allowlist bị từ chối theo cấu hình chung.

PUT trả 200 `{grade:{id,studentId,subjectId,scoreType,score,term,schoolYear}}`,
tạo mới hoặc cập nhật theo khóa `(student_id,subject_id,school_year,term,score_type)`.
`GradeService.save` mở một transaction gồm đọc snapshot cũ, upsert có điều kiện
phân công ngay trong SQL, đọc bản ghi mới và gọi `AuditService.record`.
Audit lưu `CREATE`/`UPDATE`, actor, student, entity ID và snapshot trước/sau.
Ghi điểm hoặc ghi audit thất bại thì transaction rollback toàn bộ; các ô của
một bảng được gửi riêng nên không có transaction chung cho cả bảng.

Sai role trả 403, chưa đăng nhập trả 401. Thiếu phân công, học sinh ngoài lớp và
ID không tồn tại trả cùng 404 `NOT_FOUND`. GVCN không có phân công môn không
được xem/nhập điểm. Path/query/body sai trả 400 `VALIDATION_ERROR` theo envelope chung.

Kiểm thử tại `backend` bằng JDK 25: `./mvnw test` (Windows: `.\mvnw.cmd test`).
`TeacherApiTests` kiểm tra quyền, IDOR, dữ liệu rỗng, bộ lọc và validation.
`GradeTransactionTests` gọi HTTP PUT không có transaction bao ngoài của test,
xác nhận commit/audit thật và dùng trigger SQLite gây lỗi để chứng minh rollback
cho cả tạo mới, cập nhật và trường hợp ghi điểm thất bại.
