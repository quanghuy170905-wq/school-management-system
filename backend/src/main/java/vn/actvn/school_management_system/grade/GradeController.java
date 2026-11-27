package vn.actvn.school_management_system.grade;

import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.actvn.school_management_system.auth.AuthUser;
import vn.actvn.school_management_system.common.response.ApiResponse;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('TEACHER')")
public class GradeController {
    private final GradeService service;

    public GradeController(GradeService service) {
        this.service = service;
    }

    @GetMapping("/classes/{id}/grades")
    public ApiResponse<?> grades(@AuthenticationPrincipal AuthUser user, @PathVariable String id,
            @RequestParam String subjectId, @RequestParam(required = false) String schoolYear,
            @RequestParam(required = false) String term, @RequestParam(required = false) String scoreType) {
        return ApiResponse.ok(service.list(user.id(), id, subjectId, schoolYear, term, scoreType));
    }

    @PutMapping("/grades")
    public ApiResponse<?> save(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody PutGradeRequest body) {
        return ApiResponse.ok(Map.of("grade", service.save(user.id(), body)));
    }
}
