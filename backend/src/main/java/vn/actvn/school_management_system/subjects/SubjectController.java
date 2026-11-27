package vn.actvn.school_management_system.subjects;

import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.actvn.school_management_system.common.response.ApiResponse;

@RestController
public class SubjectController {
    private final SubjectRepository repository;

    public SubjectController(SubjectRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/api/subjects")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ApiResponse<?> list() {
        return ApiResponse.ok(Map.of("items", repository.findAll()));
    }
}
