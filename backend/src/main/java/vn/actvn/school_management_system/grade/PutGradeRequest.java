package vn.actvn.school_management_system.grade;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

/** One cell per request. Actor, timestamps and the grade ID are server-owned. */
public record PutGradeRequest(
        @NotBlank @Pattern(regexp = "[0-9]+") String classId,
        @NotBlank @Pattern(regexp = "[0-9]+") String subjectId,
        @NotBlank @Pattern(regexp = "[0-9]+") String studentId,
        @NotNull @Pattern(regexp = "TX1|TX2|GK|CK") String scoreType,
        @NotNull @Pattern(regexp = "HK1|HK2") String term,
        @NotNull @Pattern(regexp = "[0-9]{4}-[0-9]{4}") String schoolYear,
        @NotNull @DecimalMin("0") @DecimalMax("10") @Digits(integer = 2, fraction = 2) BigDecimal score
) {}
