package vn.actvn.school_management_system.student;

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
import vn.actvn.school_management_system.auth.AuthUser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite::memory:", "app.db.dir=target/parent-test-data",
        "server.servlet.session.persistent=false"
})
@AutoConfigureMockMvc
@Transactional
class StudentApiTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private RequestPostProcessor as(long id, String role) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                new AuthUser(id, "test@school.test", "Test", role), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    @Test
    void realLoginSessionListsOnlyLinkedChildren() throws Exception {
        var login = mvc.perform(post("/api/auth/login").header("Origin", "http://localhost:5173")
                        .contentType("application/json")
                        .content("{\"email\":\"ph01@actvn.edu.vn\",\"password\":\"Demo@2026\"}"))
                .andExpect(status().isOk()).andReturn();
        var session = (org.springframework.mock.web.MockHttpSession) login.getRequest().getSession(false);
        mvc.perform(get("/api/students").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.items[0].id").value("1"))
                .andExpect(jsonPath("$.data.items[1].id").value("15"))
                .andExpect(jsonPath("$.data.items[0].class.id").value("1"))
                .andExpect(jsonPath("$.data.items[0].studentCode").value("AT200101"))
                .andExpect(jsonPath("$.data.items[0].dateOfBirth").value("2005-03-12"))
                .andExpect(jsonPath("$.data.items[0].passwordHash").doesNotExist());
    }

    @Test
    void guardianLinkAndParentWithoutChildrenAreSupported() throws Exception {
        mvc.perform(get("/api/students").with(as(16, "PARENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].id").value("12"));
        mvc.perform(get("/api/students").with(as(18, "PARENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items", hasSize(0)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/students", "/api/students/1/grades", "/api/students/1/timetable"})
    void requiresParentRoleAndAuthentication(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
        for (String role : List.of("TEACHER", "ADMIN")) {
            mvc.perform(get(path).with(as(2, role))).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"grades", "timetable"})
    void otherChildUnlinkedChildAndMissingIdHaveIdenticalNotFoundResponses(String resource) throws Exception {
        String expected = mvc.perform(get("/api/students/99999/" + resource).with(as(8, "PARENT")))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        for (String id : List.of("2", "5")) {
            mvc.perform(get("/api/students/" + id + "/" + resource).with(as(8, "PARENT")))
                    .andExpect(status().isNotFound()).andExpect(content().string(expected));
        }
        jdbc.update("DELETE FROM leave_requests WHERE student_id = 15");
        jdbc.update("DELETE FROM parent_students WHERE student_id = 15");
        mvc.perform(get("/api/students/15/" + resource).with(as(8, "PARENT")))
                .andExpect(status().isNotFound()).andExpect(content().string(expected));
    }

    @Test
    void gradesHaveStringIdsAndAllOptionalFiltersApply() throws Exception {
        mvc.perform(get("/api/students/1/grades").with(as(8, "PARENT"))
                        .param("schoolYear", "2024-2025").param("term", "HK1").param("subjectId", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.studentId").value("1"))
                .andExpect(jsonPath("$.data.items", hasSize(4)))
                .andExpect(jsonPath("$.data.items[0].id").isString())
                .andExpect(jsonPath("$.data.items[0].subject.id").value("1"))
                .andExpect(jsonPath("$.data.items[0].scoreType").value("TX1"))
                .andExpect(jsonPath("$.data.items[0].score").value(8.5));
        for (String query : List.of("schoolYear=2098-2099", "term=HK2&subjectId=1", "subjectId=99999")) {
            mvc.perform(get("/api/students/1/grades?" + query).with(as(8, "PARENT")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.items", hasSize(0)));
        }
    }

    @Test
    void timetableIsScopedToTheChildClassAndFilters() throws Exception {
        jdbc.update("UPDATE timetable SET room = 'OWNED' WHERE class_id = 1");
        mvc.perform(get("/api/students/1/timetable?schoolYear=2024-2025&term=HK1").with(as(8, "PARENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.studentId").value("1"))
                .andExpect(jsonPath("$.data.items", hasSize(42)))
                .andExpect(jsonPath("$.data.items[0].weekday").value(2))
                .andExpect(jsonPath("$.data.items[0].period").value(1))
                .andExpect(jsonPath("$.data.items[0].subject.id").value("4"))
                .andExpect(jsonPath("$.data.items[0].room").value("OWNED"));
        mvc.perform(get("/api/students/1/timetable?schoolYear=2098-2099").with(as(8, "PARENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items", hasSize(0)));
    }

    @Test
    void linkedStudentWithNoGradesOrTimetableReturnsEmptyLists() throws Exception {
        jdbc.update("DELETE FROM grades WHERE student_id = 15");
        jdbc.update("DELETE FROM timetable WHERE class_id = 3");
        for (String resource : List.of("grades", "timetable")) {
            mvc.perform(get("/api/students/15/" + resource).with(as(8, "PARENT")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.items", hasSize(0)));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM parent_students WHERE student_id = 15", Integer.class))
                .isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/students/0/grades", "/api/students/-1/timetable", "/api/students/abc/grades",
            "/api/students/9223372036854775808/grades", "/api/students/1/grades?subjectId=1%20OR%201=1",
            "/api/students/1/grades?subjectId=", "/api/students/1/grades?subjectId=0",
            "/api/students/1/grades?term=HK3", "/api/students/1/grades?schoolYear=2024",
            "/api/students/1/timetable?schoolYear=", "/api/students/1/timetable?term=all"
    })
    void malformedIdsAndFiltersReturnValidationEnvelope(String path) throws Exception {
        mvc.perform(get(path).with(as(8, "PARENT"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
