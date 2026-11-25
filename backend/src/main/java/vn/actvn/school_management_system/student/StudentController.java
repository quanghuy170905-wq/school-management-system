package vn.actvn.school_management_system.student;

import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.actvn.school_management_system.auth.AuthUser;
import vn.actvn.school_management_system.common.response.ApiResponse;

@RestController
@RequestMapping("/api/students")
@PreAuthorize("hasRole('PARENT')")
public class StudentController {
    private final StudentService service;

    public StudentController(StudentService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<?> list(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(Map.of("items", service.list(user.id())));
    }

    @GetMapping("/{id}/grades")
    public ApiResponse<?> grades(@AuthenticationPrincipal AuthUser user, @PathVariable String id,
            @RequestParam(required = false) String schoolYear, @RequestParam(required = false) String term,
            @RequestParam(required = false) String subjectId) {
        return ApiResponse.ok(service.grades(user.id(), id, schoolYear, term, subjectId));
    }

    @GetMapping("/{id}/timetable")
    public ApiResponse<?> timetable(@AuthenticationPrincipal AuthUser user, @PathVariable String id,
            @RequestParam(required = false) String schoolYear, @RequestParam(required = false) String term) {
        return ApiResponse.ok(service.timetable(user.id(), id, schoolYear, term));
    }
}
