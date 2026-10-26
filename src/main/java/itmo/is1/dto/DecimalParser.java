package itmo.is1.dto;

import itmo.is1.service.ApiException;

import java.math.BigDecimal;

public final class DecimalParser {

    private DecimalParser() {
    }

    private static BigDecimal toDecimal(String raw, String field, String label) {
        if (raw == null || raw.isBlank()) {
            throw ApiException.validation(field, label + ": значение не заполнено");
        }
        String normalized = raw.trim().replace(',', '.').replace(" ", "");
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException e) {
            throw ApiException.validation(field,
                    label + ": «" + raw.trim() + "» не является числом");
        }
    }

    public static double parseDouble(String raw, String field, String label) {
        double value = toDecimal(raw, field, label).doubleValue();
        if (Double.isInfinite(value) || Double.isNaN(value)) {
            throw ApiException.validation(field, label + ": значение выходит за допустимый диапазон");
        }
        return value;
    }

    public static float parseFloat(String raw, String field, String label) {
        float value = toDecimal(raw, field, label).floatValue();
        if (Float.isInfinite(value) || Float.isNaN(value)) {
            throw ApiException.validation(field, label + ": значение выходит за допустимый диапазон");
        }
        return value;
    }

    public static long parseLong(String raw, String field, String label) {
        BigDecimal value = toDecimal(raw, field, label);
        if (value.stripTrailingZeros().scale() > 0) {
            throw ApiException.validation(field, label + " должна быть целым числом");
        }
        try {
            return value.longValueExact();
        } catch (ArithmeticException e) {
            throw ApiException.validation(field, label + ": значение выходит за допустимый диапазон");
        }
    }

    public static String format(Number value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Long || value instanceof Integer) {
            return value.toString();
        }
        return new BigDecimal(value.toString()).stripTrailingZeros().toPlainString();
    }
}
