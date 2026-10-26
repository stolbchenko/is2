package itmo.is1.service;

import itmo.is1.entity.Color;
import itmo.is1.entity.Coordinates;
import itmo.is1.entity.Location;
import itmo.is1.entity.Person;
import itmo.is1.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UniquenessRulesTest {

    private InMemoryPersonRepository repository;
    private UniquenessRules rules;
    private User owner;

    @BeforeEach
    void setUp() {
        repository = new InMemoryPersonRepository();
        rules = new UniquenessRules();
        rules.repository = repository;
        owner = repository.addUser("alice");
    }

    @Test
    void rejectsSameNameIgnoringCaseAndSpaces() {
        Person ivan = repository.addPerson("Иван Петров", owner);

        ApiException e = assertThrows(ApiException.class,
                () -> rules.requireUniqueName("  иван петров ", null));
        assertEquals(409, e.getStatus());
        assertEquals("name", e.getField());
        assertEquals("Человек с именем «иван петров» уже существует (ID " + ivan.getId()
                + "), имена должны быть уникальны", e.getMessage());
    }

    @Test
    void acceptsDifferentName() {
        repository.addPerson("Иван", owner);
        assertDoesNotThrow(() -> rules.requireUniqueName("Иван Петров", null));
    }

    @Test
    void personDoesNotConflictWithItselfOnUpdate() {
        Person ivan = repository.addPerson("Иван", owner);
        Person petr = repository.addPerson("Пётр", owner);

        assertDoesNotThrow(() -> rules.requireUniqueName("ИВАН", ivan.getId()));
        assertThrows(ApiException.class, () -> rules.requireUniqueName("Иван", petr.getId()));
    }

    @Test
    void rejectsDuplicateCoordinates() {
        Coordinates existing = repository.addCoordinates(1.5f, -2);

        ApiException e = assertThrows(ApiException.class,
                () -> rules.requireUniqueCoordinates(new Coordinates(1.5f, -2.0)));
        assertEquals(409, e.getStatus());
        assertEquals("coordX", e.getField());
        assertEquals("Координаты (1.5; -2) уже существуют (ID " + existing.getId()
                + ") — выберите их из списка существующих", e.getMessage());
        assertDoesNotThrow(() -> rules.requireUniqueCoordinates(new Coordinates(1.5f, 2.0)));
    }

    @Test
    void rejectsDuplicateLocation() {
        Location existing = repository.addLocation(0.5, 7, -1);

        ApiException e = assertThrows(ApiException.class,
                () -> rules.requireUniqueLocation(new Location(0.5, 7, -1.0)));
        assertEquals("locX", e.getField());
        assertEquals("Локация (0.5; 7; -1) уже существует (ID " + existing.getId()
                + ") — выберите её из списка существующих", e.getMessage());
        assertDoesNotThrow(() -> rules.requireUniqueLocation(new Location(0.5, 8, -1.0)));
    }

    private static Person transientPerson(String name, float cx, double cy, double lx, long ly, double lz) {
        Person person = new Person();
        person.setName(name);
        person.setHairColor(Color.BLACK);
        person.setHeight(1f);
        person.setCoordinates(new Coordinates(cx, cy));
        person.setLocation(new Location(lx, ly, lz));
        return person;
    }

    private List<String> checkImport(Map<Integer, Person> batch) {
        List<String> errors = new ArrayList<>();
        rules.checkImport(batch, (recordNo, message) -> errors.add(recordNo + ": " + message));
        return errors;
    }

    @Test
    void importAcceptsDistinctRecords() {
        Map<Integer, Person> batch = new LinkedHashMap<>();
        batch.put(1, transientPerson("A", 1, 1, 1, 1, 1));
        batch.put(2, transientPerson("B", 1, 2, 1, 1, 2));
        assertEquals(List.of(), checkImport(batch));
    }

    @Test
    void importDetectsDuplicatesInsideFile() {
        Map<Integer, Person> batch = new LinkedHashMap<>();
        batch.put(1, transientPerson("Анна", 1, 1, 1, 1, 1));
        batch.put(3, transientPerson(" АННА", 1, 1, 1, 1, 1));
        batch.put(4, transientPerson("Борис", -0.0f, 5, 2, 2, 2));
        batch.put(5, transientPerson("Вера", 0.0f, 5, 3, 3, 3));

        assertEquals(List.of(
                "3: имя «АННА» уже задано в записи №1, имена должны быть уникальны",
                "3: координаты (1; 1) уже заданы в записи №1, координаты должны быть уникальны",
                "3: локация (1; 1; 1) уже задана в записи №1, локации должны быть уникальны",
                "5: координаты (0; 5) уже заданы в записи №4, координаты должны быть уникальны"),
                checkImport(batch));
    }

    @Test
    void importDetectsConflictsWithExistingObjects() {
        Person ivan = repository.addPerson("Иван", owner);
        Coordinates coordinates = repository.addCoordinates(3, 4);
        Location location = repository.addLocation(5, 6, 7);

        Map<Integer, Person> batch = new LinkedHashMap<>();
        batch.put(1, transientPerson("иван", 3, 4, 5, 6, 7));

        assertEquals(List.of(
                "1: Человек с именем «иван» уже существует (ID " + ivan.getId() + "), имена должны быть уникальны",
                "1: Координаты (3; 4) уже существуют (ID " + coordinates.getId()
                        + ") — выберите их из списка существующих",
                "1: Локация (5; 6; 7) уже существует (ID " + location.getId()
                        + ") — выберите её из списка существующих"),
                checkImport(batch));
    }
}
