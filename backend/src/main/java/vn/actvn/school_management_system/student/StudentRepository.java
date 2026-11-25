package vn.actvn.school_management_system.student;

import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import vn.actvn.school_management_system.common.exception.AppException;
import vn.actvn.school_management_system.student.StudentViews.*;

@Repository
public class StudentRepository {
    private final JdbcClient jdbc;

    public StudentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Student> findLinked(long parentId) {
        return jdbc.sql("""
                SELECT s.id, s.student_code, s.full_name, s.date_of_birth,
                       c.id AS class_id, c.name AS class_name, c.class_code, c.school_year
                FROM parent_students ps
                JOIN students s ON s.id = ps.student_id
                JOIN classes c ON c.id = s.class_id
                WHERE ps.parent_id = :parentId
                ORDER BY s.student_code, s.id
                """).param("parentId", parentId).query((rs, row) -> new Student(
                rs.getString("id"), rs.getString("student_code"), rs.getString("full_name"),
                rs.getString("date_of_birth"), new ClassView(rs.getString("class_id"),
                rs.getString("class_name"), rs.getString("class_code"), rs.getString("school_year"))
        )).list();
    }

    public Grades findGrades(long parentId, long studentId, String schoolYear, String term, Long subjectId) {
        // Filters stay in the LEFT JOIN: an owned student with no matching grades still yields a row.
        // Ownership is enforced in this data query, not by a separate preflight check.
        var rows = jdbc.sql("""
                SELECT g.id, g.score_type, g.score, g.term, g.school_year,
                       sub.id AS subject_id, sub.name AS subject_name
                FROM parent_students ps
                JOIN students s ON s.id = ps.student_id
                LEFT JOIN grades g ON g.student_id = s.id
                    AND (:schoolYear IS NULL OR g.school_year = :schoolYear)
                    AND (:term IS NULL OR g.term = :term)
                    AND (:subjectId IS NULL OR g.subject_id = :subjectId)
                LEFT JOIN subjects sub ON sub.id = g.subject_id
                WHERE ps.parent_id = :parentId AND s.id = :studentId
                ORDER BY g.school_year, g.term, sub.id,
                         CASE g.score_type WHEN 'TX1' THEN 1 WHEN 'TX2' THEN 2 WHEN 'GK' THEN 3 ELSE 4 END,
                         g.id
                """).param("parentId", parentId).param("studentId", studentId)
                .param("schoolYear", schoolYear).param("term", term).param("subjectId", subjectId)
                .query((rs, row) -> new GradeRow(rs.getString("id") == null ? null : new Grade(
                        rs.getString("id"), new Subject(rs.getString("subject_id"), rs.getString("subject_name")),
                        rs.getString("score_type"), rs.getBigDecimal("score"),
                        rs.getString("term"), rs.getString("school_year")))).list();
        if (rows.isEmpty()) throw AppException.notFound();
        return new Grades(String.valueOf(studentId), rows.stream().map(GradeRow::grade)
                .filter(Objects::nonNull).toList());
    }

    public Timetable findTimetable(long parentId, long studentId, String schoolYear, String term) {
        var rows = jdbc.sql("""
                SELECT t.id, t.weekday, t.period, t.room, t.school_year, t.term,
                       sub.id AS subject_id, sub.name AS subject_name
                FROM parent_students ps
                JOIN students s ON s.id = ps.student_id
                LEFT JOIN timetable t ON t.class_id = s.class_id
                    AND (:schoolYear IS NULL OR t.school_year = :schoolYear)
                    AND (:term IS NULL OR t.term = :term)
                LEFT JOIN subjects sub ON sub.id = t.subject_id
                WHERE ps.parent_id = :parentId AND s.id = :studentId
                ORDER BY t.school_year, t.term, t.weekday, t.period, t.id
                """).param("parentId", parentId).param("studentId", studentId)
                .param("schoolYear", schoolYear).param("term", term)
                .query((rs, row) -> new LessonRow(rs.getString("id") == null ? null : new Lesson(
                        rs.getInt("weekday"), rs.getInt("period"),
                        new Subject(rs.getString("subject_id"), rs.getString("subject_name")),
                        rs.getString("room"), rs.getString("school_year"), rs.getString("term")))).list();
        if (rows.isEmpty()) throw AppException.notFound();
        return new Timetable(String.valueOf(studentId), rows.stream().map(LessonRow::lesson)
                .filter(Objects::nonNull).toList());
    }

    private record GradeRow(Grade grade) {}
    private record LessonRow(Lesson lesson) {}
}
