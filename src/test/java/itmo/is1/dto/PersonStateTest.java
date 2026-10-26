package itmo.is1.dto;

import itmo.is1.dto.PersonState.FieldChange;
import itmo.is1.entity.Color;
import itmo.is1.entity.Coordinates;
import itmo.is1.entity.Country;
import itmo.is1.entity.Location;
import itmo.is1.entity.Person;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonStateTest {

    private static Person person() {
        Person person = new Person();
        person.setName("Иван");
        person.setEyeColor(Color.BLUE);
        person.setHairColor(Color.BLACK);
        person.setHeight(180.5f);
        person.setNationality(Country.RUSSIA);
        person.setCoordinates(new Coordinates(10.5f, 200.0));
        person.setLocation(new Location(1.5, -7L, -3.25));
        return person;
    }

    @Test
    void capturesEveryEditableValueInApiFormat() {
        assertEquals(new PersonState("Иван", "BLUE", "BLACK", "180.5", "RUSSIA",
                "10.5", "200", "1.5", "-7", "-3.25"), PersonState.of(person()));
    }

    @Test
    void optionalValuesStayNull() {
        Person person = person();
        person.setEyeColor(null);
        person.setNationality(null);

        PersonState state = PersonState.of(person);

        assertEquals(null, state.eyeColor());
        assertEquals(null, state.nationality());
    }

    @Test
    void survivesJsonRoundTrip() {
        PersonState state = PersonState.of(person());

        PersonState restored = PersonState.fromJson(state.toJson());

        assertEquals(state, restored);
        assertTrue(state.toJson().contains("\"height\":\"180.5\""), state.toJson());
    }

    @Test
    void roundTripKeepsNullsAndBigNumbers() {
        Person person = person();
        person.setEyeColor(null);
        person.setLocation(new Location(0.1, Long.MIN_VALUE, 1e-3));
        person.setHeight(0.001f);

        PersonState state = PersonState.of(person);

        assertEquals(state, PersonState.fromJson(state.toJson()));
        assertEquals("-9223372036854775808", state.locY());
    }

    @Test
    void unreadableJsonIsReportedClearly() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> PersonState.fromJson("{не json"));
        assertTrue(e.getMessage().contains("Запись журнала повреждена"), e.getMessage());
    }

    @Test
    void diffListsOnlyChangedFieldsWithRussianLabels() {
        PersonState before = PersonState.of(person());
        Person changed = person();
        changed.setName("Пётр");
        changed.setEyeColor(null);
        changed.setLocation(new Location(1.5, 9L, -3.25));

        assertEquals(List.of(
                        new FieldChange("name", "Имя", "Иван", "Пётр"),
                        new FieldChange("eyeColor", "Цвет глаз", "BLUE", null),
                        new FieldChange("locY", "Локация Y", "-7", "9")),
                PersonState.diff(before, PersonState.of(changed)));
    }

    @Test
    void identicalStatesHaveNoDiff() {
        assertEquals(List.of(), PersonState.diff(PersonState.of(person()), PersonState.of(person())));
    }

    @Test
    void creationAndDeletionDiffAgainstNothing() {
        PersonState state = PersonState.of(person());

        List<FieldChange> created = PersonState.diff(null, state);
        List<FieldChange> deleted = PersonState.diff(state, null);

        assertEquals(10, created.size());
        assertTrue(created.stream().allMatch(c -> c.before() == null && c.after() != null));
        assertEquals(10, deleted.size());
        assertTrue(deleted.stream().allMatch(c -> c.after() == null && c.before() != null));
    }
}
