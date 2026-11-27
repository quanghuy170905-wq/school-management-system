package vn.actvn.school_management_system.classroom;

import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import vn.actvn.school_management_system.common.exception.AppException;

@Repository
public class ClassroomRepository {
    private final JdbcClient jdbc;

    public ClassroomRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record TeachingClass(String classId, String className, String subjectId,
                                String subjectName, boolean isHomeroom) {}
    public record Student(String id, String fullName, String studentCode) {}

    public List<TeachingClass> findForTeacher(long teacherId) {
        // A homeroom-only class has one row with null subject fields. Assignments
        // belonging to other teachers must never be exposed through the homeroom relation.
        return jdbc.sql("""
                SELECT c.id AS class_id, c.name AS class_name, sub.id AS subject_id,
                       sub.name AS subject_name, c.homeroom_teacher_id = :teacherId AS is_homeroom
                FROM classes c
                LEFT JOIN teaching_assignments ta ON ta.class_id = c.id AND ta.teacher_id = :teacherId
                LEFT JOIN subjects sub ON sub.id = ta.subject_id
                WHERE c.homeroom_teacher_id = :teacherId OR ta.id IS NOT NULL
                ORDER BY c.class_code, c.id, sub.subject_code, sub.id
                """).param("teacherId", teacherId).query((rs, row) -> new TeachingClass(
                rs.getString("class_id"), rs.getString("class_name"), rs.getString("subject_id"),
                rs.getString("subject_name"), rs.getBoolean("is_homeroom"))).list();
    }

    public List<Student> findStudents(long teacherId, long classId) {
        var rows = jdbc.sql("""
                SELECT s.id, s.full_name, s.student_code
                FROM classes c
                LEFT JOIN students s ON s.class_id = c.id
                WHERE c.id = :classId AND (c.homeroom_teacher_id = :teacherId OR EXISTS (
                    SELECT 1 FROM teaching_assignments ta
                    WHERE ta.class_id = c.id AND ta.teacher_id = :teacherId
                ))
                ORDER BY s.student_code, s.id
                """).param("teacherId", teacherId).param("classId", classId)
                .query((rs, row) -> new StudentRow(rs.getString("id") == null ? null : new Student(
                        rs.getString("id"), rs.getString("full_name"), rs.getString("student_code")))).list();
        if (rows.isEmpty()) throw AppException.notFound();
        return rows.stream().map(StudentRow::student).filter(Objects::nonNull).toList();
    }

    private record StudentRow(Student student) {}
}
