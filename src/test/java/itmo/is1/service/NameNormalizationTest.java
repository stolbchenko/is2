package itmo.is1.service;

import itmo.is1.dto.PersonImportRecord;
import itmo.is1.dto.PersonRequest.CoordinatesRequest;
import itmo.is1.dto.PersonRequest.LocationRequest;
import itmo.is1.entity.Color;
import itmo.is1.entity.Person;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Репозиторий в памяти сравнивает имена как PostgreSQL TRIM (только пробелы), как и настоящая БД. */
class NameNormalizationTest {

    private final ServiceFixture f = new ServiceFixture();

    @Test
    void cleanNameStripsWhitespaceControlAndInvisibleCharactersAtTheEdgesOnly() {
        assertEquals("Иван Петров", UniquenessRules.cleanName(" \t\n\r Иван Петров    　"));
        assertEquals("Иван", UniquenessRules.cleanName("﻿​Иван​\u0000"));
        assertEquals("Иван  Петров", UniquenessRules.cleanName("Иван  Петров"), "внутренние пробелы не трогаются");
        assertEquals("", UniquenessRules.cleanName(" \t "));
        assertEquals(null, UniquenessRules.cleanName(null));
    }

    @Test
    void nameIsStoredCleaned() {
        assertEquals("Таб", f.create(f.alice, "\tТаб\n").getName());
        assertEquals("Неразрывный", f.create(f.alice, "Неразрывный ").getName());
    }

    @Test
    void tabAtTheEdgeDoesNotBypassUniqueness() {
        f.create(f.alice, "\tТаб");

        ApiException e = assertThrows(ApiException.class, () -> f.create(f.bob, "Таб"));
        assertEquals(409, e.getStatus());
        assertEquals(1, f.repository.persons.size());
    }

    @Test
    void reversedOrder_plainNameFirstThenTabbed() {
        f.create(f.alice, "Таб");

        assertEquals(409, assertThrows(ApiException.class, () -> f.create(f.bob, "таб\t")).getStatus());
    }

    @Test
    void nonBreakingSpaceAtTheEdgeDoesNotBypassUniqueness() {
        f.create(f.alice, "Неразрывный ");

        assertEquals(409, assertThrows(ApiException.class, () -> f.create(f.bob, "неразрывный")).getStatus());
        assertEquals(409, assertThrows(ApiException.class, () -> f.create(f.bob, " Неразрывный")).getStatus());
    }

    @Test
    void renamingToSomeoneElsesNameWithInvisibleEdgesIsRejected() {
        f.create(f.alice, "Анна");
        Person boris = f.create(f.bob, "Борис");

        f.as(f.bob);
        ApiException e = assertThrows(ApiException.class,
                () -> f.service.update(boris.getId(), ServiceFixture.withSame(boris, "​Анна\t", "170")));
        assertEquals(409, e.getStatus());
        assertEquals("Борис", boris.getName());
    }

    @Test
    void nameOfInvisibleCharactersOnlyIsEmpty() {
        f.as(f.alice);
        ApiException e = assertThrows(ApiException.class, () -> f.service.create(f.newPerson(" ​\t")));
        assertEquals(400, e.getStatus());
        assertEquals("name", e.getField());
        assertEquals(0, f.repository.persons.size());
    }

    @Test
    void importStoresCleanedNamesAndTheyTakePartInUniqueness() {
        f.as(f.alice);
        f.service.importAll(List.of(record(" Импорт\t", "1"), record("﻿Второй ", "2")));

        assertEquals(List.of("Импорт", "Второй"), f.repository.persons.stream().map(Person::getName).toList());
        assertEquals(409, assertThrows(ApiException.class, () -> f.create(f.bob, "импорт")).getStatus());
    }

    @Test
    void importWithNamesDifferingOnlyByInvisibleEdgesIsRejectedEntirely() {
        f.as(f.alice);
        ImportException e = assertThrows(ImportException.class,
                () -> f.service.importAll(List.of(record("Дубль", "1"), record("дубль ", "2"))));

        assertEquals(List.of("Запись №2: имя «дубль» уже задано в записи №1, имена должны быть уникальны"),
                e.getErrors());
        assertEquals(0, f.repository.persons.size());
    }

    @Test
    void importRecordWithInvisibleOnlyNameIsReportedAsEmpty() {
        f.as(f.alice);
        ImportException e = assertThrows(ImportException.class,
                () -> f.service.importAll(List.of(record(" ", "1"))));

        assertEquals(List.of("Запись №1: Имя не может быть пустым"), e.getErrors());
    }

    private static PersonImportRecord record(String name, String n) {
        return new PersonImportRecord(name, new CoordinatesRequest(n, "-" + n), null, Color.BLACK,
                new LocationRequest(n, n, "-" + n), "170", null);
    }
}
