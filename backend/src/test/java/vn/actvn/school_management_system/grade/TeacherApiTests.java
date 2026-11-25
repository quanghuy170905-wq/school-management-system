package vn.actvn.school_management_system.grade;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import vn.actvn.school_management_system.auth.AuthUser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite::memory:", "app.db.dir=target/teacher-test-data",
        "server.servlet.session.persistent=false"
})
@AutoConfigureMockMvc
@Transactional
class TeacherApiTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    static RequestPostProcessor as(long id, String role) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                new AuthUser(id, "test@school.test", "Test", role), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    static String body(String classId, String subjectId, String studentId, String score) {
        return """
                {"classId":"%s","subjectId":"%s","studentId":"%s","scoreType":"TX1",
                 "term":"HK1","schoolYear":"2024-2025","score":%s}
                """.formatted(classId, subjectId, studentId, score);
    }

    @Test
    void teacherClassesContainOnlyOwnAssignmentsAndHomeroom() throws Exception {
        var response = mvc.perform(get("/api/teacher/classes").with(as(2, "TEACHER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items", hasSize(14)))
                .andReturn().getResponse().getContentAsString();
        for (var item : mapper.readTree(response).path("data").path("items")) {
            assertThat(item.path("classId").isString()).isTrue();
            assertThat(item.path("subjectId").asString()).isIn("1", "9", "10", "11", "12");
            assertThat(item.path("isHomeroom").asBoolean()).isEqualTo(item.path("classId").asString().equals("1"));
        }
        mvc.perform(get("/api/teacher/classes").with(as(6, "TEACHER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].classId").value("2"))
                .andExpect(jsonPath("$.data.items[0].subjectId").isEmpty())
                .andExpect(jsonPath("$.data.items[0].subjectName").isEmpty())
                .andExpect(jsonPath("$.data.items[0].isHomeroom").value(true));
        mvc.perform(get("/api/teacher/classes").with(as(7, "TEACHER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items", hasSize(0)));
    }

    @Test
    void rosterAllowsHomeroomOrAssignedTeacherWithoutDuplicatingStudents() throws Exception {
        for (long teacherId : List.of(2L, 6L)) {
            mvc.perform(get("/api/classes/2/students").with(as(teacherId, "TEACHER")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.items", hasSize(5)))
                    .andExpect(jsonPath("$.data.items[0].id").value("6"))
                    .andExpect(jsonPath("$.data.items[0].studentCode").value("CT190201"));
        }
        jdbc.update("INSERT INTO classes VALUES (99, 'Empty', 'EMPTY', '2026-2027', 7)");
        mvc.perform(get("/api/classes/99/students").with(as(7, "TEACHER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items", hasSize(0)));
    }

    @Test
    void classGradesRetainStudentsMissingScoresAndApplyEveryFilter() throws Exception {
        mvc.perform(get("/api/classes/1/grades?subjectId=1&schoolYear=2024-2025&term=HK1&scoreType=CK")
                        .with(as(2, "TEACHER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.classId").value("1"))
                .andExpect(jsonPath("$.data.subjectId").value("1"))
                .andExpect(jsonPath("$.data.items", hasSize(5)))
                .andExpect(jsonPath("$.data.items[0].grades", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].grades[0].scoreType").value("CK"))
                .andExpect(jsonPath("$.data.items[0].grades[0].id").isString())
                .andExpect(jsonPath("$.data.items[4].studentId").value("5"))
                .andExpect(jsonPath("$.data.items[4].grades", hasSize(0)));
        var empty = mvc.perform(get("/api/classes/1/grades?subjectId=1&schoolYear=2098-2099")
                        .with(as(2, "TEACHER"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(5))).andReturn().getResponse().getContentAsString();
        for (var item : mapper.readTree(empty).path("data").path("items")) {
            assertThat(item.path("grades").size()).isZero();
        }
        mvc.perform(get("/api/classes/1/grades?subjectId=1").with(as(2, "TEACHER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].grades", hasSize(4)));
        jdbc.update("INSERT INTO classes VALUES (99, 'Empty', 'EMPTY', '2026-2027', 7)");
        jdbc.update("INSERT INTO teaching_assignments (teacher_id,class_id,subject_id) VALUES (7,99,1)");
        mvc.perform(get("/api/classes/99/grades?subjectId=1").with(as(7, "TEACHER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items", hasSize(0)));
    }

    @Test
    void subjectsAllowTeacherAndAdminButDenyParent() throws Exception {
        for (String role : List.of("TEACHER", "ADMIN")) {
            mvc.perform(get("/api/subjects").with(as(2, role))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items", hasSize(12)))
                    .andExpect(jsonPath("$.data.items[0].id").isString())
                    .andExpect(jsonPath("$.data.items[0].subjectCode").isString());
        }
        mvc.perform(get("/api/subjects").with(as(8, "PARENT"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/subjects")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/teacher/classes", "/api/classes/1/students", "/api/classes/1/grades?subjectId=1"})
    void teacherReadsRequireAuthenticationAndCorrectRole(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
        for (String role : List.of("PARENT", "ADMIN")) {
            mvc.perform(get(path).with(as(8, role))).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        }
    }

    @Test
    void outOfScopeAndMissingObjectsHaveTheSameNotFoundResponse() throws Exception {
        String expected = mvc.perform(get("/api/classes/99999/students").with(as(7, "TEACHER")))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        for (String path : List.of("/api/classes/1/students", "/api/classes/1/grades?subjectId=1")) {
            mvc.perform(get(path).with(as(7, "TEACHER"))).andExpect(status().isNotFound())
                    .andExpect(content().string(expected));
        }
        // Homeroom status never substitutes for a matching subject assignment.
        mvc.perform(get("/api/classes/2/grades?subjectId=1").with(as(6, "TEACHER")))
                .andExpect(status().isNotFound()).andExpect(content().string(expected));
        mvc.perform(get("/api/classes/1/grades?subjectId=2").with(as(2, "TEACHER")))
                .andExpect(status().isNotFound()).andExpect(content().string(expected));
        jdbc.update("DELETE FROM timetable WHERE class_id=1 AND subject_id=1");
        jdbc.update("DELETE FROM teaching_assignments WHERE class_id=1 AND subject_id=1");
        mvc.perform(get("/api/classes/1/grades?subjectId=1").with(as(2, "TEACHER")))
                .andExpect(status().isNotFound()).andExpect(content().string(expected));
        mvc.perform(put("/api/grades").with(as(2, "TEACHER"))
                        .header("Origin", "http://localhost:5173").contentType("application/json")
                        .content(body("1", "1", "1", "9")))
                .andExpect(status().isNotFound()).andExpect(content().string(expected));
    }

    @Test
    void writesRejectOtherClassOtherSubjectUnassignedTeacherAndWrongRole() throws Exception {
        int logs = jdbc.queryForObject("SELECT count(*) FROM audit_logs", Integer.class);
        for (String payload : List.of(body("1", "1", "6", "9"), body("1", "2", "1", "9"),
                body("99999", "1", "1", "9"), body("1", "1", "99999", "9"))) {
            mvc.perform(put("/api/grades").with(as(2, "TEACHER"))
                            .header("Origin", "http://localhost:5173").contentType("application/json").content(payload))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        }
        for (long teacherId : List.of(6L, 7L)) {
            mvc.perform(put("/api/grades").with(as(teacherId, "TEACHER"))
                            .header("Origin", "http://localhost:5173").contentType("application/json")
                            .content(body("2", "1", "6", "9")))
                    .andExpect(status().isNotFound());
        }
        for (String role : List.of("PARENT", "ADMIN")) {
            mvc.perform(put("/api/grades").with(as(8, role))
                            .header("Origin", "http://localhost:5173").contentType("application/json")
                            .content(body("1", "1", "1", "9")))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(put("/api/grades").header("Origin", "http://localhost:5173")
                        .contentType("application/json").content(body("1", "1", "1", "9")))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs", Integer.class)).isEqualTo(logs);
        assertThat(jdbc.queryForObject("SELECT score FROM grades WHERE student_id=1 AND subject_id=1 AND score_type='TX1'",
                Double.class)).isEqualTo(8.5);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "10.01", "8.123", "null", "\"NaN\"", "\"Infinity\"", "1e100"})
    void invalidScoresDoNotWriteOrAudit(String score) throws Exception {
        int logs = jdbc.queryForObject("SELECT count(*) FROM audit_logs", Integer.class);
        mvc.perform(put("/api/grades").with(as(2, "TEACHER"))
                        .header("Origin", "http://localhost:5173").contentType("application/json")
                        .content(body("1", "1", "1", score)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs", Integer.class)).isEqualTo(logs);
    }

    @ParameterizedTest
    @ValueSource(strings = {"classId:0", "studentId:9223372036854775808", "subjectId:1 OR 1=1",
            "term:HK3", "scoreType:TX3", "schoolYear:2024", "updatedBy:2", "id:1"})
    void invalidFieldsAndMassAssignmentReturnValidationErrors(String change) throws Exception {
        var parts = change.split(":", 2);
        var payload = mapper.readTree(body("1", "1", "1", "8"));
        ((tools.jackson.databind.node.ObjectNode) payload).put(parts[0], parts[1]);
        mvc.perform(put("/api/grades").with(as(2, "TEACHER"))
                        .header("Origin", "http://localhost:5173").contentType("application/json")
                        .content(mapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void missingBodyFieldsAndOriginAreRejected() throws Exception {
        mvc.perform(put("/api/grades").with(as(2, "TEACHER"))
                        .header("Origin", "http://localhost:5173").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/grades").with(as(2, "TEACHER"))
                        .contentType("application/json").content(body("1", "1", "1", "9")))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/classes/0/students", "/api/classes/abc/students", "/api/classes/1/grades",
            "/api/classes/1/grades?subjectId=", "/api/classes/1/grades?subjectId=-1",
            "/api/classes/1/grades?subjectId=1&term=HK3", "/api/classes/1/grades?subjectId=1&scoreType=ALL",
            "/api/classes/1/grades?subjectId=1&schoolYear=2024"})
    void invalidReadParametersReturnValidationErrors(String path) throws Exception {
        mvc.perform(get(path).with(as(2, "TEACHER"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
