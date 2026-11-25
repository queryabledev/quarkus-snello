package dev.queryable.snello.runtime;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

/**
 * Scalar type support: which Java types can be filtered through query parameters
 * and how a raw query parameter string is coerced to the field type.
 */
public final class SnelloTypes {

    private static final Set<String> SCALAR_TYPES = Set.of(
            "boolean", "byte", "short", "int", "long", "float", "double", "char",
            "java.lang.Boolean", "java.lang.Byte", "java.lang.Short", "java.lang.Integer",
            "java.lang.Long", "java.lang.Float", "java.lang.Double", "java.lang.Character",
            "java.lang.String",
            "java.math.BigDecimal", "java.math.BigInteger",
            "java.util.UUID",
            "java.util.Date", "java.sql.Date", "java.sql.Timestamp",
            "java.time.LocalDate", "java.time.LocalDateTime", "java.time.LocalTime",
            "java.time.OffsetDateTime", "java.time.ZonedDateTime", "java.time.Instant");

    private SnelloTypes() {
    }

    public static boolean isScalar(String typeName, boolean enumType) {
        return enumType || SCALAR_TYPES.contains(typeName);
    }

    public static boolean isScalar(Class<?> type) {
        return isScalar(type.getName(), type.isEnum());
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static Object convert(Class<?> type, String value) {
        if (type == String.class)
            return value;
        if (type == Boolean.class || type == boolean.class) {
            if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                throw new IllegalArgumentException("Expected true or false but got: " + value);
            }
            return Boolean.valueOf(value);
        }
        if (type == Integer.class || type == int.class)
            return Integer.valueOf(value);
        if (type == Long.class || type == long.class)
            return Long.valueOf(value);
        if (type == Short.class || type == short.class)
            return Short.valueOf(value);
        if (type == Byte.class || type == byte.class)
            return Byte.valueOf(value);
        if (type == Double.class || type == double.class)
            return Double.valueOf(value);
        if (type == Float.class || type == float.class)
            return Float.valueOf(value);
        if (type == Character.class || type == char.class) {
            if (value.length() != 1) {
                throw new IllegalArgumentException("Expected a single character but got: " + value);
            }
            return value.charAt(0);
        }
        if (type == UUID.class)
            return UUID.fromString(value);
        if (type == BigDecimal.class)
            return new BigDecimal(value);
        if (type == BigInteger.class)
            return new BigInteger(value);
        if (type == Date.class)
            return legacyDate(value);
        if (type == java.sql.Date.class)
            return java.sql.Date.valueOf(LocalDate.parse(value));
        if (type == java.sql.Timestamp.class)
            return java.sql.Timestamp.valueOf(LocalDateTime.parse(value));
        if (type == LocalDate.class)
            return LocalDate.parse(value);
        if (type == LocalDateTime.class)
            return LocalDateTime.parse(value);
        if (type == LocalTime.class)
            return LocalTime.parse(value);
        if (type == OffsetDateTime.class)
            return OffsetDateTime.parse(value);
        if (type == ZonedDateTime.class)
            return ZonedDateTime.parse(value);
        if (type == Instant.class)
            return Instant.parse(value);
        if (type.isEnum())
            return Enum.valueOf((Class<? extends Enum>) type, value);
        throw new IllegalArgumentException("Unsupported query parameter type: " + type.getName());
    }

    private static Date legacyDate(String value) {
        try {
            return Date.from(OffsetDateTime.parse(value).toInstant());
        } catch (DateTimeParseException ignored) {
            // Fall back to local formats interpreted as UTC.
        }
        try {
            return Date.from(LocalDateTime.parse(value).toInstant(ZoneOffset.UTC));
        } catch (DateTimeParseException ignored) {
            // Fall back to a plain date.
        }
        return Date.from(LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC));
    }
}
