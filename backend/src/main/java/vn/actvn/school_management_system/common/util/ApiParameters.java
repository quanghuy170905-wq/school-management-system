package vn.actvn.school_management_system.common.util;

import java.util.Set;
import vn.actvn.school_management_system.common.exception.AppException;

/** Strict parsing shared by the academic APIs; omitted filters mean all values. */
public final class ApiParameters {
    private ApiParameters() {}

    public static long id(String value) {
        if (value == null || !value.matches("[0-9]+")) throw invalid();
        try {
            long id = Long.parseLong(value);
            if (id <= 0) throw invalid();
            return id;
        } catch (NumberFormatException ex) {
            throw invalid();
        }
    }

    public static Long optionalId(String value) {
        return value == null ? null : id(value);
    }

    public static String schoolYear(String value) {
        if (value != null && !value.matches("[0-9]{4}-[0-9]{4}")) throw invalid();
        return value;
    }

    public static String term(String value) {
        return oneOf(value, Set.of("HK1", "HK2"));
    }

    public static String scoreType(String value) {
        return oneOf(value, Set.of("TX1", "TX2", "GK", "CK"));
    }

    private static String oneOf(String value, Set<String> allowed) {
        if (value != null && !allowed.contains(value)) throw invalid();
        return value;
    }

    private static AppException invalid() {
        return AppException.validation("Tham số không hợp lệ.");
    }
}
