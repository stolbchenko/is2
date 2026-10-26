package itmo.is1.service;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import itmo.is1.dto.PersonImportRecord;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Разбирает файл импорта: JSON-массив записей {@link PersonImportRecord}.
 * Проверяет только структуру файла; ограничения предметной области проверяет {@link PersonService}.
 */
public final class PersonImportParser {

    public static final String FORMAT_ERROR = "Файл не соответствует формату импорта, ни один объект не создан";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private PersonImportParser() {
    }

    public static List<PersonImportRecord> parse(InputStream content) {
        JsonNode root;
        try {
            root = MAPPER.readTree(content);
        } catch (JsonProcessingException e) {
            throw new ImportException(FORMAT_ERROR,
                    List.of("Файл не является корректным JSON" + describeLocation(e.getLocation())));
        } catch (IOException e) {
            throw new ImportException("Не удалось прочитать загруженный файл");
        }

        if (root == null || root.isMissingNode()) {
            throw new ImportException(FORMAT_ERROR, List.of("Файл пуст"));
        }
        if (!root.isArray()) {
            throw new ImportException(FORMAT_ERROR,
                    List.of("Корневым элементом файла должен быть массив записей […]"));
        }
        if (root.isEmpty()) {
            throw new ImportException(FORMAT_ERROR, List.of("Файл не содержит ни одной записи"));
        }

        List<PersonImportRecord> records = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < root.size(); i++) {
            JsonNode node = root.get(i);
            String prefix = "Запись №" + (i + 1) + ": ";
            if (!node.isObject()) {
                errors.add(prefix + "ожидается объект {…}");
                continue;
            }
            try {
                records.add(MAPPER.treeToValue(node, PersonImportRecord.class));
            } catch (JsonMappingException e) {
                errors.add(prefix + describe(e));
            } catch (JsonProcessingException e) {
                errors.add(prefix + "запись не удалось разобрать");
            }
        }

        if (!errors.isEmpty()) {
            throw new ImportException(FORMAT_ERROR, errors);
        }
        return records;
    }

    private static String describe(JsonMappingException e) {
        String field = fieldPath(e);
        if (e instanceof UnrecognizedPropertyException) {
            return "неизвестное поле «" + field + "»";
        }
        if (e instanceof InvalidFormatException invalid && invalid.getTargetType() != null
                && invalid.getTargetType().isEnum()) {
            String allowed = Arrays.stream(invalid.getTargetType().getEnumConstants())
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));
            return "поле «" + field + "»: значение «" + invalid.getValue()
                    + "» недопустимо, допустимые значения: " + allowed;
        }
        return field.isEmpty()
                ? "запись не удалось разобрать"
                : "некорректное значение поля «" + field + "»";
    }

    private static String fieldPath(JsonMappingException e) {
        return e.getPath().stream()
                .map(JsonMappingException.Reference::getFieldName)
                .filter(name -> name != null)
                .collect(Collectors.joining("."));
    }

    private static String describeLocation(JsonLocation location) {
        if (location == null || location.getLineNr() < 0) {
            return "";
        }
        return " (строка " + location.getLineNr() + ", столбец " + location.getColumnNr() + ")";
    }
}
