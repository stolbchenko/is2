package itmo.is1.service;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportServiceTest {

    @Test
    void describesFailureWithAllErrors() {
        ImportException error = new ImportException("Импорт отменён",
                List.of("Запись №1: Имя не может быть пустым", "Запись №2: Рост должен быть больше 0"));

        assertEquals("Импорт отменён\nЗапись №1: Имя не может быть пустым\nЗапись №2: Рост должен быть больше 0",
                ImportService.describe(error));
    }

    @Test
    void describesFailureWithoutDetails() {
        assertEquals("Файл для импорта не передан",
                ImportService.describe(new ImportException("Файл для импорта не передан")));
    }

    @Test
    void truncatesVeryLongDescription() {
        ImportException error = new ImportException("Импорт отменён",
                Collections.nCopies(1000, "Запись №1: Координата X не может превышать 91"));

        String description = ImportService.describe(error);
        assertEquals(4000, description.length());
        assertTrue(description.endsWith("…"));
    }
}
