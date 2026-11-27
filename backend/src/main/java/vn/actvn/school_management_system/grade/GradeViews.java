package vn.actvn.school_management_system.grade;

import java.math.BigDecimal;
import java.util.List;

public final class GradeViews {
    private GradeViews() {}

    public record Grade(String id, String studentId, String subjectId, String scoreType,
                        BigDecimal score, String term, String schoolYear) {}
    public record Cell(String id, String scoreType, BigDecimal score, String term, String schoolYear) {}
    public record StudentGrades(String studentId, String studentName, List<Cell> grades) {}
    public record ClassGrades(String classId, String subjectId, List<StudentGrades> items) {}
}
