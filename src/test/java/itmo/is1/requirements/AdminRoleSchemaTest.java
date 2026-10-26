package itmo.is1.requirements;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Схема не должна ограничивать число администраторов: роль — обычная колонка у каждого пользователя. */
class AdminRoleSchemaTest {

    private static String schema() throws IOException {
        return Files.readString(Path.of("src/main/resources/schema.sql"), StandardCharsets.UTF_8)
                .toUpperCase(Locale.ROOT);
    }

    private static String userTable(String schema) {
        Matcher m = Pattern.compile("CREATE TABLE IF NOT EXISTS APP_USER \\((.*?)\\n\\);", Pattern.DOTALL).matcher(schema);
        assertTrue(m.find(), "в schema.sql нет таблицы app_user");
        return m.group(1);
    }

    @Test
    void roleIsAPlainColumnWithTwoAllowedValues() throws IOException {
        String table = userTable(schema());

        assertTrue(table.contains("ROLE TEXT NOT NULL DEFAULT 'USER' CHECK (ROLE IN ('USER', 'ADMIN'))"), table);
    }

    @Test
    void onlyTheUsernameIsUnique_soAnyNumberOfUsersMayShareTheAdminRole() throws IOException {
        String schema = schema();
        String table = userTable(schema);

        assertEquals(1, table.split("UNIQUE", -1).length - 1, "в app_user единственный UNIQUE — на логине: " + table);
        assertTrue(table.contains("USERNAME TEXT NOT NULL UNIQUE"), table);
        assertFalse(schema.contains("UNIQUE INDEX"), "уникальный индекс (например, по роли) ограничил бы число администраторов");
        assertFalse(schema.contains("CREATE TRIGGER"), "триггеры в схеме не используются");
    }
}
