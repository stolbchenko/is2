package itmo.is1.requirements;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Требование ЛР2: новые ограничения уникальности (имя человека, значения координат и локации)
 * реализуются только в бизнес-логике и не должны быть отражены в БД.
 */
class UniquenessNotInDatabaseTest {

    private static String schema() throws IOException {
        try (InputStream in = UniquenessNotInDatabaseTest.class.getResourceAsStream("/schema.sql")) {
            assertNotNull(in, "schema.sql не найден в ресурсах");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).toUpperCase(Locale.ROOT);
        }
    }

    private static String tableDefinition(String schema, String table) {
        Matcher m = Pattern.compile("CREATE TABLE IF NOT EXISTS " + table + " \\((.*?)\\);", Pattern.DOTALL)
                .matcher(schema);
        assertTrue(m.find(), "в schema.sql нет таблицы " + table);
        return m.group(1);
    }

    @Test
    void domainTablesHaveNoUniqueConstraints() throws IOException {
        String schema = schema();
        for (String table : new String[]{"PERSON", "COORDINATES", "LOCATION"}) {
            assertFalse(tableDefinition(schema, table).contains("UNIQUE"), "UNIQUE в таблице " + table);
        }
    }

    @Test
    void noUniqueIndexesOrAddedConstraints() throws IOException {
        String schema = schema();
        assertFalse(schema.contains("UNIQUE INDEX"), "в schema.sql есть уникальный индекс");
        assertFalse(Pattern.compile("ALTER TABLE (PERSON|COORDINATES|LOCATION)[^;]*UNIQUE").matcher(schema).find(),
                "уникальность добавлена через ALTER TABLE");
    }
}
