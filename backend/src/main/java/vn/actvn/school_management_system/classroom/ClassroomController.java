package vn.actvn.school_management_system.classroom;

import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.actvn.school_management_system.auth.AuthUser;
import vn.actvn.school_management_system.common.response.ApiResponse;
import vn.actvn.school_management_system.common.util.ApiParameters;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('TEACHER')")
public class ClassroomController {
    private final ClassroomRepository repository;

    public ClassroomController(ClassroomRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/teacher/classes")
    public ApiResponse<?> classes(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(Map.of("items", repository.findForTeacher(user.id())));
    }

    @GetMapping("/classes/{id}/students")
    public ApiResponse<?> students(@AuthenticationPrincipal AuthUser user, @PathVariable String id) {
        return ApiResponse.ok(Map.of("items", repository.findStudents(user.id(), ApiParameters.id(id))));
    }
}
