package vn.actvn.school_management_system.subjects;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SubjectRepository {
    private final JdbcClient jdbc;

    public SubjectRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record Subject(String id, String name, String subjectCode) {}

    public List<Subject> findAll() {
        return jdbc.sql("SELECT id, name, subject_code FROM subjects ORDER BY subject_code, id")
                .query((rs, row) -> new Subject(rs.getString("id"), rs.getString("name"),
                        rs.getString("subject_code"))).list();
    }
}
