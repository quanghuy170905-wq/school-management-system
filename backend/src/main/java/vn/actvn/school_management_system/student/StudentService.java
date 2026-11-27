package vn.actvn.school_management_system.student;

import java.util.List;
import org.springframework.stereotype.Service;
import vn.actvn.school_management_system.common.util.ApiParameters;
import vn.actvn.school_management_system.student.StudentViews.*;

@Service
public class StudentService {
    private final StudentRepository repository;

    public StudentService(StudentRepository repository) {
        this.repository = repository;
    }

    public List<Student> list(long parentId) {
        return repository.findLinked(parentId);
    }

    public Grades grades(long parentId, String studentId, String schoolYear, String term, String subjectId) {
        return repository.findGrades(parentId, ApiParameters.id(studentId), ApiParameters.schoolYear(schoolYear),
                ApiParameters.term(term), ApiParameters.optionalId(subjectId));
    }

    public Timetable timetable(long parentId, String studentId, String schoolYear, String term) {
        return repository.findTimetable(parentId, ApiParameters.id(studentId),
                ApiParameters.schoolYear(schoolYear), ApiParameters.term(term));
    }
}
