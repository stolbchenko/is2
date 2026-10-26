package itmo.is1.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import itmo.is1.entity.Person;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Снимок редактируемых значений объекта для журнала и отката. Координаты и локация хранятся
 * по значению, а не по ID: к моменту отката они могут быть изменены или удалены.
 */
public record PersonState(
        String name,
        String eyeColor,
        String hairColor,
        String height,
        String nationality,
        String coordX,
        String coordY,
        String locX,
        String locY,
        String locZ
) {

    public record FieldChange(String field, String label, String before, String after) {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static PersonState of(Person person) {
        return new PersonState(
                person.getName(),
                nameOf(person.getEyeColor()),
                nameOf(person.getHairColor()),
                DecimalParser.format(person.getHeight()),
                nameOf(person.getNationality()),
                DecimalParser.format(person.getCoordinates().getX()),
                DecimalParser.format(person.getCoordinates().getY()),
                DecimalParser.format(person.getLocation().getX()),
                DecimalParser.format(person.getLocation().getY()),
                DecimalParser.format(person.getLocation().getZ()));
    }

    private static String nameOf(Enum<?> value) {
        return value == null ? null : value.name();
    }

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось сохранить состояние объекта", e);
        }
    }

    public static PersonState fromJson(String json) {
        try {
            return MAPPER.readValue(json, PersonState.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Запись журнала повреждена: состояние объекта не читается", e);
        }
    }

    public static List<FieldChange> diff(PersonState before, PersonState after) {
        List<FieldChange> result = new ArrayList<>();
        add(result, "name", "Имя", before == null ? null : before.name, after == null ? null : after.name);
        add(result, "height", "Рост", before == null ? null : before.height, after == null ? null : after.height);
        add(result, "hairColor", "Цвет волос",
                before == null ? null : before.hairColor, after == null ? null : after.hairColor);
        add(result, "eyeColor", "Цвет глаз",
                before == null ? null : before.eyeColor, after == null ? null : after.eyeColor);
        add(result, "nationality", "Гражданство",
                before == null ? null : before.nationality, after == null ? null : after.nationality);
        add(result, "coordX", "Координата X",
                before == null ? null : before.coordX, after == null ? null : after.coordX);
        add(result, "coordY", "Координата Y",
                before == null ? null : before.coordY, after == null ? null : after.coordY);
        add(result, "locX", "Локация X", before == null ? null : before.locX, after == null ? null : after.locX);
        add(result, "locY", "Локация Y", before == null ? null : before.locY, after == null ? null : after.locY);
        add(result, "locZ", "Локация Z", before == null ? null : before.locZ, after == null ? null : after.locZ);
        return result;
    }

    private static void add(List<FieldChange> result, String field, String label, String before, String after) {
        if (!Objects.equals(before, after)) {
            result.add(new FieldChange(field, label, before, after));
        }
    }
}
