package vn.actvn.school_management_system.grade;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import vn.actvn.school_management_system.common.exception.AppException;
import vn.actvn.school_management_system.grade.GradeViews.*;

@Repository
public class GradeRepository {
    private final JdbcClient jdbc;

    public GradeRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public ClassGrades findByClass(long teacherId, long classId, long subjectId,
                                   String schoolYear, String term, String scoreType) {
        var rows = jdbc.sql("""
                SELECT s.id AS student_id, s.full_name AS student_name,
                       g.id, g.score_type, g.score, g.term, g.school_year
                FROM teaching_assignments ta
                JOIN classes c ON c.id = ta.class_id
                LEFT JOIN students s ON s.class_id = c.id
                LEFT JOIN grades g ON g.student_id = s.id AND g.subject_id = ta.subject_id
                    AND (:schoolYear IS NULL OR g.school_year = :schoolYear)
                    AND (:term IS NULL OR g.term = :term)
                    AND (:scoreType IS NULL OR g.score_type = :scoreType)
                WHERE ta.teacher_id = :teacherId AND ta.class_id = :classId AND ta.subject_id = :subjectId
                ORDER BY s.student_code, s.id, g.school_year, g.term,
                         CASE g.score_type WHEN 'TX1' THEN 1 WHEN 'TX2' THEN 2 WHEN 'GK' THEN 3 ELSE 4 END, g.id
                """).param("teacherId", teacherId).param("classId", classId).param("subjectId", subjectId)
                .param("schoolYear", schoolYear).param("term", term).param("scoreType", scoreType)
                .query((rs, row) -> new ClassRow(rs.getString("student_id"), rs.getString("student_name"),
                        rs.getString("id") == null ? null : new Cell(rs.getString("id"),
                                rs.getString("score_type"), rs.getBigDecimal("score"),
                                rs.getString("term"), rs.getString("school_year")))).list();
        if (rows.isEmpty()) throw AppException.notFound();
        var students = new LinkedHashMap<String, StudentGrades>();
        for (var row : rows) {
            if (row.studentId() == null) continue;
            var student = students.computeIfAbsent(row.studentId(), id ->
                    new StudentGrades(id, row.studentName(), new ArrayList<>()));
            if (row.cell() != null) student.grades().add(row.cell());
        }
        return new ClassGrades(String.valueOf(classId), String.valueOf(subjectId),
                students.values().stream().map(s -> new StudentGrades(s.studentId(), s.studentName(),
                        java.util.List.copyOf(s.grades()))).toList());
    }

    public Optional<Grade> findCurrent(long teacherId, long classId, long subjectId, long studentId,
                                       String schoolYear, String term, String scoreType) {
        var rows = jdbc.sql("""
                SELECT g.id, g.student_id, g.subject_id, g.score_type, g.score, g.term, g.school_year
                FROM students s
                JOIN teaching_assignments ta ON ta.class_id = s.class_id
                    AND ta.teacher_id = :teacherId AND ta.subject_id = :subjectId
                LEFT JOIN grades g ON g.student_id = s.id AND g.subject_id = ta.subject_id
                    AND g.school_year = :schoolYear AND g.term = :term AND g.score_type = :scoreType
                WHERE s.id = :studentId AND s.class_id = :classId
                """).param("teacherId", teacherId).param("classId", classId).param("subjectId", subjectId)
                .param("studentId", studentId).param("schoolYear", schoolYear).param("term", term)
                .param("scoreType", scoreType).query((rs, row) -> new GradeRow(
                        rs.getString("id") == null ? null : new Grade(rs.getString("id"),
                                rs.getString("student_id"), rs.getString("subject_id"), rs.getString("score_type"),
                                rs.getBigDecimal("score"), rs.getString("term"), rs.getString("school_year")))).list();
        if (rows.isEmpty()) throw AppException.notFound();
        return Optional.ofNullable(rows.getFirst().grade());
    }

    public void upsert(long teacherId, long classId, long subjectId, long studentId, String schoolYear,
                       String term, String scoreType, BigDecimal score, String updatedAt) {
        // Repeat the assignment and class constraints in the mutation itself. All
        // values, including updated_by, come from validated input or the session.
        int changed = jdbc.sql("""
                INSERT INTO grades (student_id, subject_id, school_year, term, score_type, score, updated_by, updated_at)
                SELECT s.id, ta.subject_id, :schoolYear, :term, :scoreType, :score, :teacherId, :updatedAt
                FROM students s
                JOIN teaching_assignments ta ON ta.class_id = s.class_id
                    AND ta.teacher_id = :teacherId AND ta.subject_id = :subjectId
                WHERE s.id = :studentId AND s.class_id = :classId
                ON CONFLICT (student_id, subject_id, school_year, term, score_type)
                DO UPDATE SET score = excluded.score, updated_by = excluded.updated_by, updated_at = excluded.updated_at
                """).param("teacherId", teacherId).param("classId", classId).param("subjectId", subjectId)
                .param("studentId", studentId).param("schoolYear", schoolYear).param("term", term)
                .param("scoreType", scoreType).param("score", score).param("updatedAt", updatedAt).update();
        if (changed != 1) throw AppException.notFound();
    }

    private record ClassRow(String studentId, String studentName, Cell cell) {}
    private record GradeRow(Grade grade) {}
}
