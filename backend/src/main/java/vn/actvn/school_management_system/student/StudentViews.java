package vn.actvn.school_management_system.student;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

public final class StudentViews {
    private StudentViews() {}

    public record ClassView(String id, String name, String classCode, String schoolYear) {}
    public record Student(String id, String studentCode, String fullName, String dateOfBirth,
                          @JsonProperty("class") ClassView classroom) {}
    public record Subject(String id, String name) {}
    public record Grade(String id, Subject subject, String scoreType, BigDecimal score,
                        String term, String schoolYear) {}
    public record Lesson(int weekday, int period, Subject subject, String room,
                         String schoolYear, String term) {}
    public record Grades(String studentId, List<Grade> items) {}
    public record Timetable(String studentId, List<Lesson> items) {}
}
