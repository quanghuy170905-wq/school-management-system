package vn.actvn.school_management_system.grade;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static vn.actvn.school_management_system.grade.TeacherApiTests.as;
import static vn.actvn.school_management_system.grade.TeacherApiTests.body;

/** No test-managed transaction: every HTTP PUT must commit or roll back its own transaction. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite::memory:", "app.db.dir=target/grade-transaction-test-data",
        "server.servlet.session.persistent=false"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class GradeTransactionTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    @Test
    void createThenUpdateCommitsOneGradeAndCompleteAuditSnapshots() throws Exception {
        String payload = body("1", "1", "5", "0").replace("TX1", "CK");
        int initialLogs = logCount();
        var created = mvc.perform(put("/api/grades").with(as(2, "TEACHER"))
                        .header("Origin", "http://localhost:5173").contentType("application/json").content(payload))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.grade.id").isString())
                .andExpect(jsonPath("$.data.grade.studentId").value("5"))
                .andExpect(jsonPath("$.data.grade.subjectId").value("1"))
                .andExpect(jsonPath("$.data.grade.scoreType").value("CK"))
                .andExpect(jsonPath("$.data.grade.score").value(0)).andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(created).path("data").path("grade").path("id").asString();
        assertThat(logCount()).isEqualTo(initialLogs + 1);
        var firstLog = jdbc.queryForMap("SELECT * FROM audit_logs ORDER BY id DESC LIMIT 1");
        assertThat(firstLog.get("action")).isEqualTo("CREATE");
        assertThat(firstLog.get("entity_type")).isEqualTo("grades");
        assertThat(firstLog.get("entity_id")).isEqualTo(id);
        assertThat(((Number) firstLog.get("student_id")).longValue()).isEqualTo(5);
        assertThat(((Number) firstLog.get("actor_user_id")).longValue()).isEqualTo(2);
        assertThat(firstLog.get("old_value")).isNull();
        assertThat(mapper.readTree((String) firstLog.get("new_value")).path("score").decimalValue())
                .isEqualByComparingTo(BigDecimal.ZERO);

        for (String score : new String[]{"8.25", "10"}) {
            mvc.perform(put("/api/grades").with(as(2, "TEACHER"))
                            .header("Origin", "http://localhost:5173").contentType("application/json")
                            .content(body("1", "1", "5", score).replace("TX1", "CK")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.grade.id").value(id));
        }
        assertThat(logCount()).isEqualTo(initialLogs + 3);
        var log = jdbc.queryForMap("SELECT * FROM audit_logs ORDER BY id DESC LIMIT 1");
        assertThat(log.get("action")).isEqualTo("UPDATE");
        assertThat(log.get("entity_id")).isEqualTo(id);
        var old = mapper.readTree((String) log.get("old_value"));
        var current = mapper.readTree((String) log.get("new_value"));
        assertThat(old.path("score").decimalValue()).isEqualByComparingTo("8.25");
        assertThat(current.path("score").decimalValue()).isEqualByComparingTo("10");
        assertThat(current.path("studentId").asString()).isEqualTo("5");
        assertThat(current.path("schoolYear").asString()).isEqualTo("2024-2025");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM grades WHERE student_id=5 AND subject_id=1 AND score_type='CK'", Integer.class))
                .isEqualTo(1);
        var grade = jdbc.queryForMap("SELECT * FROM grades WHERE id=?", Long.parseLong(id));
        assertThat(((Number) grade.get("score")).doubleValue()).isEqualTo(10);
        assertThat(((Number) grade.get("updated_by")).longValue()).isEqualTo(2);
        assertThat((String) grade.get("updated_at")).endsWith("Z");
    }

    @Test
    void auditInsertFailureRollsBackExistingGradeAndMetadata() throws Exception {
        var before = jdbc.queryForMap("SELECT * FROM grades WHERE student_id=1 AND subject_id=1 AND score_type='TX1'");
        int initialLogs = logCount();
        failAudit();
        mvc.perform(put("/api/grades").with(as(2, "TEACHER"))
                        .header("Origin", "http://localhost:5173").contentType("application/json")
                        .content(body("1", "1", "1", "9.75")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.data").doesNotExist());
        assertThat(jdbc.queryForMap("SELECT * FROM grades WHERE id=?", before.get("id"))).isEqualTo(before);
        assertThat(logCount()).isEqualTo(initialLogs);
    }

    @Test
    void auditInsertFailureRollsBackNewGrade() throws Exception {
        int initialLogs = logCount();
        failAudit();
        mvc.perform(put("/api/grades").with(as(2, "TEACHER"))
                        .header("Origin", "http://localhost:5173").contentType("application/json")
                        .content(body("1", "1", "5", "9.75").replace("TX1", "CK")))
                .andExpect(status().isInternalServerError());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM grades WHERE student_id=5 AND subject_id=1 AND score_type='CK'", Integer.class))
                .isZero();
        assertThat(logCount()).isEqualTo(initialLogs);
    }

    @Test
    void failedGradeWriteNeverLeavesAnAuditRecord() throws Exception {
        int initialLogs = logCount();
        jdbc.execute("""
                CREATE TRIGGER fail_grade BEFORE INSERT ON grades
                BEGIN SELECT RAISE(ABORT, 'test grade storage failure'); END
                """);
        mvc.perform(put("/api/grades").with(as(2, "TEACHER"))
                        .header("Origin", "http://localhost:5173").contentType("application/json")
                        .content(body("1", "1", "5", "9.75").replace("TX1", "CK")))
                .andExpect(status().isInternalServerError());
        assertThat(logCount()).isEqualTo(initialLogs);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM grades WHERE student_id=5 AND subject_id=1 AND score_type='CK'", Integer.class))
                .isZero();
    }

    private int logCount() {
        return jdbc.queryForObject("SELECT count(*) FROM audit_logs", Integer.class);
    }

    private void failAudit() {
        jdbc.execute("""
                CREATE TRIGGER fail_grade_audit BEFORE INSERT ON audit_logs WHEN NEW.entity_type = 'grades'
                BEGIN SELECT RAISE(ABORT, 'test audit storage failure'); END
                """);
    }
}
