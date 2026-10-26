package itmo.is1.requirements;

import itmo.is1.entity.Person;
import jakarta.persistence.Column;
import jakarta.persistence.Version;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptimisticLockSchemaTest {

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8).toUpperCase(Locale.ROOT);
    }

    private static String personTable(String schema) {
        Matcher m = Pattern.compile("CREATE TABLE IF NOT EXISTS PERSON \\((.*?)\\n\\);", Pattern.DOTALL).matcher(schema);
        assertTrue(m.find(), "нет таблицы person");
        return m.group(1);
    }

    @Test
    void personEntityHasVersionField() throws NoSuchFieldException {
        Field version = Person.class.getDeclaredField("version");
        assertNotNull(version.getAnnotation(Version.class), "поле version должно быть помечено @Version");
        assertEquals("version", version.getAnnotation(Column.class).name());
    }

    @Test
    void schemaHasVersionColumnAndMigratesExistingDatabases() throws IOException {
        String schema = read("src/main/resources/schema.sql");

        assertTrue(personTable(schema).contains("VERSION INTEGER NOT NULL DEFAULT 1"), personTable(schema));
        assertTrue(schema.contains("ALTER TABLE PERSON ADD COLUMN IF NOT EXISTS VERSION INTEGER NOT NULL DEFAULT 1"),
                "БД, созданные до появления версии, должны получить колонку при повторном запуске schema.sql");
    }
}
