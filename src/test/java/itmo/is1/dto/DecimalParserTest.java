package itmo.is1.dto;

import itmo.is1.service.ApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DecimalParserTest {

    @Test
    void acceptsDotAsSeparator() {
        assertEquals(12.5, DecimalParser.parseDouble("12.5", "f", "Поле"), 0.0001);
    }

    @Test
    void acceptsCommaAsSeparator() {
        assertEquals(12.5, DecimalParser.parseDouble("12,5", "f", "Поле"), 0.0001);
    }

    @Test
    void acceptsCommaForFloat() {
        assertEquals(180.5f, DecimalParser.parseFloat("180,5", "height", "Рост"), 0.0001f);
    }

    @Test
    void rejectsNonNumericWithReadableMessage() {
        ApiException e = assertThrows(ApiException.class,
                () -> DecimalParser.parseFloat("abc", "height", "Рост"));
        assertEquals("height", e.getField());
        assertEquals("Рост: «abc» не является числом", e.getMessage());
    }

    @Test
    void rejectsBlank() {
        ApiException e = assertThrows(ApiException.class,
                () -> DecimalParser.parseFloat("  ", "height", "Рост"));
        assertEquals("Рост: значение не заполнено", e.getMessage());
    }

    @Test
    void rejectsFractionalWhereIntegerRequired() {
        ApiException e = assertThrows(ApiException.class,
                () -> DecimalParser.parseLong("5.5", "locY", "Координата Y"));
        assertEquals("Координата Y должна быть целым числом", e.getMessage());
    }

    @Test
    void acceptsIntegerWithTrailingZeros() {
        assertEquals(5L, DecimalParser.parseLong("5.00", "locY", "Координата Y"));
    }

    @Test
    void rejectsValueOutOfFloatRange() {
        ApiException e = assertThrows(ApiException.class,
                () -> DecimalParser.parseFloat("1e400", "height", "Рост"));
        assertEquals("Рост: значение выходит за допустимый диапазон", e.getMessage());
    }

    @Test
    void formatsWithoutExponent() {
        assertEquals("0.00000001", DecimalParser.format(0.00000001d));
    }

    @Test
    void formatsLongAsPlainNumber() {
        assertEquals("9007199254740993", DecimalParser.format(9007199254740993L));
    }
}
