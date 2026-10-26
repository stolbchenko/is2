package itmo.is1.service;

import itmo.is1.dto.PersonImportRecord;
import itmo.is1.entity.Color;
import itmo.is1.entity.Country;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonImportParserTest {

    private static List<PersonImportRecord> parse(String json) {
        return PersonImportParser.parse(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    private static List<String> errorsOf(String json) {
        return assertThrows(ImportException.class, () -> parse(json)).getErrors();
    }

    @Test
    void parsesNestedObjectsFromSameRecord() {
        List<PersonImportRecord> records = parse("""
                [{"name": "Иван", "coordinates": {"x": 10.5, "y": "20,25"},
                  "eyeColor": "BLUE", "hairColor": "BLACK",
                  "location": {"x": 1, "y": 2, "z": 3.5}, "height": 180.5, "nationality": "RUSSIA"},
                 {"name": "Пётр", "coordinates": {"x": 1, "y": 2}, "hairColor": "ORANGE",
                  "location": {"x": 1, "y": 2, "z": 3}, "height": "170"}]
                """);

        assertEquals(2, records.size());
        PersonImportRecord first = records.get(0);
        assertEquals("Иван", first.name());
        assertEquals("10.5", first.coordinates().x());
        assertEquals("20,25", first.coordinates().y());
        assertEquals(Color.BLUE, first.eyeColor());
        assertEquals("3.5", first.location().z());
        assertEquals("180.5", first.height());
        assertEquals(Country.RUSSIA, first.nationality());
        assertNull(records.get(1).eyeColor());
        assertNull(records.get(1).nationality());
    }

    @Test
    void rejectsBrokenJsonWithPosition() {
        List<String> errors = errorsOf("[{\"name\": \"Иван\",]");
        assertTrue(errors.get(0).startsWith("Файл не является корректным JSON (строка 1"), errors.toString());
    }

    @Test
    void rejectsEmptyFile() {
        assertEquals(List.of("Файл пуст"), errorsOf(""));
    }

    @Test
    void rejectsNonArrayRoot() {
        assertEquals(List.of("Корневым элементом файла должен быть массив записей […]"), errorsOf("{}"));
    }

    @Test
    void rejectsEmptyArray() {
        assertEquals(List.of("Файл не содержит ни одной записи"), errorsOf("[]"));
    }

    @Test
    void reportsAllBrokenRecordsWithNumbers() {
        List<String> errors = errorsOf("""
                [{"name": "ok", "hairColor": "BLACK"},
                 {"name": "x", "hairColor": "RED"},
                 {"name": "y", "coordinatesId": 5},
                 42,
                 {"name": "z", "coordinates": {"x": 1, "y": 2, "w": 3}}]
                """);

        assertEquals(4, errors.size(), errors.toString());
        assertEquals("Запись №2: поле «hairColor»: значение «RED» недопустимо, "
                + "допустимые значения: BLACK, BLUE, YELLOW, ORANGE", errors.get(0));
        assertEquals("Запись №3: неизвестное поле «coordinatesId»", errors.get(1));
        assertEquals("Запись №4: ожидается объект {…}", errors.get(2));
        assertEquals("Запись №5: неизвестное поле «coordinates.w»", errors.get(3));
    }
}
