package itmo.is1.requirements;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Сверка настроек, которые не поймать обычными юнит-тестами и которые проявляются только на
 * живом сервере: схема БД журнала и регистрация сущностей в persistence.xml.
 */
class ChangeLogSetupTest {

    private static String read(String path) throws IOException {
        return Files.readString(new File(path).toPath(), StandardCharsets.UTF_8);
    }

    private static String journalTable() throws IOException {
        Matcher m = Pattern.compile("CREATE TABLE IF NOT EXISTS person_change \\((.*?)\\);", Pattern.DOTALL)
                .matcher(read("src/main/resources/schema.sql"));
        assertTrue(m.find(), "в schema.sql нет таблицы person_change");
        return m.group(1).toUpperCase(Locale.ROOT);
    }

    @Test
    void everyEntityIsRegisteredInPersistenceXml() throws IOException {
        String persistence = read("src/main/resources/META-INF/persistence.xml");
        List<String> missing = new ArrayList<>();
        File[] files = new File("src/main/java/itmo/is1/entity").listFiles((dir, name) -> name.endsWith(".java"));
        for (File file : files) {
            if (Files.readString(file.toPath(), StandardCharsets.UTF_8).contains("@Entity")) {
                String cls = file.getName().replace(".java", "");
                if (!persistence.contains("<class>itmo.is1.entity." + cls + "</class>")) {
                    missing.add(cls);
                }
            }
        }
        assertTrue(missing.isEmpty(), "сущности не зарегистрированы в persistence.xml: " + missing);
    }

    @Test
    void journalTableConstrainsActionAndStates() throws IOException {
        String table = journalTable();

        assertTrue(table.contains("ACTION IN ('CREATED', 'UPDATED', 'DELETED', 'ROLLED_BACK')"), table);
        assertTrue(table.contains("(ACTION = 'CREATED') = (BEFORE_STATE IS NULL)"), "создание — без «до»");
        assertTrue(table.contains("(ACTION = 'DELETED') = (AFTER_STATE IS NULL)"), "удаление — без «после»");
        assertTrue(table.contains("ACTOR_ID INTEGER NOT NULL REFERENCES APP_USER"), table);
        assertTrue(table.contains("OWNER_ID INTEGER NOT NULL REFERENCES APP_USER"), table);
    }

    @Test
    void journalOutlivesDeletedObjects() throws IOException {
        String table = journalTable();

        assertTrue(table.contains("PERSON_ID INTEGER NOT NULL,"), "person_id — просто число: " + table);
        assertFalse(table.contains("REFERENCES PERSON "), "внешний ключ на person не даст удалить объект");
        assertFalse(table.contains("REFERENCES PERSON("), "внешний ключ на person не даст удалить объект");
    }

    @Test
    void journalAddsNoUniquenessToDatabase() throws IOException {
        assertFalse(journalTable().contains("UNIQUE"), "в журнале нет уникальных ограничений");
    }
}
