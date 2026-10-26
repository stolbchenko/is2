package itmo.is1.service;

import itmo.is1.dto.PersonImportRecord;
import itmo.is1.dto.PersonRequest.CoordinatesRequest;
import itmo.is1.dto.PersonRequest.LocationRequest;
import itmo.is1.entity.Color;
import itmo.is1.entity.Country;
import itmo.is1.entity.Person;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PersonImportValidationTest {

    private static PersonImportRecord record(String name, String coordX, String coordY, Color hair,
                                             String locY, String height) {
        return new PersonImportRecord(name, new CoordinatesRequest(coordX, coordY), Color.BLUE, hair,
                new LocationRequest("1.5", locY, "-3"), height, Country.FRANCE);
    }

    private static List<String> errors(PersonImportRecord record) {
        List<String> errors = new ArrayList<>();
        assertNull(PersonService.buildImported(record, errors));
        return errors;
    }

    private static String error(PersonImportRecord record) {
        List<String> errors = errors(record);
        assertEquals(1, errors.size(), errors.toString());
        return errors.get(0);
    }

    @Test
    void buildsPersonWithNestedObjects() {
        List<String> errors = new ArrayList<>();
        Person person = PersonService.buildImported(
                record("  Анна ", "91", "599", Color.BLACK, "7", "165,5"), errors);

        assertEquals(List.of(), errors);

        assertEquals("Анна", person.getName());
        assertEquals(165.5f, person.getHeight());
        assertEquals(Color.BLACK, person.getHairColor());
        assertEquals(Color.BLUE, person.getEyeColor());
        assertEquals(Country.FRANCE, person.getNationality());
        assertEquals(91f, person.getCoordinates().getX());
        assertEquals(599.0, person.getCoordinates().getY());
        assertEquals(1.5, person.getLocation().getX());
        assertEquals(7L, person.getLocation().getY());
        assertEquals(-3.0, person.getLocation().getZ());
    }

    @Test
    void appliesDomainConstraints() {
        assertEquals("Имя не может быть пустым", error(record(" ", "1", "1", Color.BLACK, "1", "1")));
        assertEquals("Цвет волос не может быть null", error(record("a", "1", "1", null, "1", "1")));
        assertEquals("Рост должен быть больше 0", error(record("a", "1", "1", Color.BLACK, "1", "0")));
        assertEquals("Рост: значение не заполнено", error(record("a", "1", "1", Color.BLACK, "1", null)));
        assertEquals("Координата X не может превышать 91",
                error(record("a", "91.01", "1", Color.BLACK, "1", "1")));
        assertEquals("Координата Y не может превышать 599",
                error(record("a", "1", "600", Color.BLACK, "1", "1")));
        assertEquals("Координата Y локации должна быть целым числом",
                error(record("a", "1", "1", Color.BLACK, "1.5", "1")));
        assertEquals("Координата Y: значение не заполнено",
                error(record("a", "1", null, Color.BLACK, "1", "1")));
    }

    @Test
    void reportsEveryInvalidFieldOfRecord() {
        assertEquals(List.of(
                        "Имя не может быть пустым",
                        "Цвет волос не может быть null",
                        "Рост должен быть больше 0",
                        "Координата X не может превышать 91",
                        "Координата Y не может превышать 599",
                        "Координата Y локации должна быть целым числом"),
                errors(record("", "92", "600", null, "1,5", "-1")));
    }

    @Test
    void requiresNestedObjects() {
        assertEquals("Не заданы координаты (coordinates)", error(new PersonImportRecord(
                "a", null, null, Color.BLACK, new LocationRequest("1", "1", "1"), "1", null)));
        assertEquals("Не задана локация (location)", error(new PersonImportRecord(
                "a", new CoordinatesRequest("1", "1"), null, Color.BLACK, null, "1", null)));
    }
}
