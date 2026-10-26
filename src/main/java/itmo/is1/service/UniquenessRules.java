package itmo.is1.service;

import itmo.is1.dto.DecimalParser;
import itmo.is1.entity.Coordinates;
import itmo.is1.entity.Location;
import itmo.is1.entity.Person;
import itmo.is1.repository.PersonRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Ограничения уникальности проверяются только здесь, в БД их нет (требование ЛР2): имя без учёта
 * регистра и невидимых символов по краям, значения новых координат и новой локации.
 */
@ApplicationScoped
public class UniquenessRules {

    @Inject
    PersonRepository repository;

    /** {@code selfId} — ID изменяемого объекта, чтобы он не конфликтовал сам с собой; null при создании. */
    public void requireUniqueName(String name, Integer selfId) {
        if (name == null || cleanName(name).isEmpty()) {
            return;
        }
        repository.findByNormalizedName(normalizeName(name)).stream()
                .filter(p -> !p.getId().equals(selfId))
                .findFirst()
                .ifPresent(p -> {
                    throw ApiException.conflict("name", nameTaken(name, p));
                });
    }

    public void requireUniqueCoordinates(Coordinates coordinates) {
        List<Coordinates> existing = repository.findCoordinatesByValue(coordinates.getX(), coordinates.getY());
        if (!existing.isEmpty()) {
            throw ApiException.conflict("coordX", coordinatesTaken(coordinates, existing.get(0)));
        }
    }

    public void requireUniqueLocation(Location location) {
        List<Location> existing =
                repository.findLocationsByValue(location.getX(), location.getY(), location.getZ());
        if (!existing.isEmpty()) {
            throw ApiException.conflict("locX", locationTaken(location, existing.get(0)));
        }
    }

    /**
     * Проверяет записи импорта и между собой, и с уже существующими объектами.
     * {@code persons} — номер записи в файле → построенный (ещё не сохранённый) объект.
     */
    public void checkImport(Map<Integer, Person> persons, BiConsumer<Integer, String> errors) {
        Map<String, Integer> names = new HashMap<>();
        Map<CoordinatesKey, Integer> coordinates = new HashMap<>();
        Map<LocationKey, Integer> locations = new HashMap<>();

        persons.forEach((recordNo, person) -> {
            Integer sameName = names.putIfAbsent(normalizeName(person.getName()), recordNo);
            if (sameName != null) {
                errors.accept(recordNo, "имя «" + cleanName(person.getName())
                        + "» уже задано в записи №" + sameName + ", имена должны быть уникальны");
            } else {
                runCheck(recordNo, errors, () -> requireUniqueName(person.getName(), null));
            }

            Coordinates c = person.getCoordinates();
            Integer sameCoordinates = coordinates.putIfAbsent(CoordinatesKey.of(c), recordNo);
            if (sameCoordinates != null) {
                errors.accept(recordNo, "координаты " + format(c) + " уже заданы в записи №" + sameCoordinates
                        + ", координаты должны быть уникальны");
            } else {
                runCheck(recordNo, errors, () -> requireUniqueCoordinates(c));
            }

            Location l = person.getLocation();
            Integer sameLocation = locations.putIfAbsent(LocationKey.of(l), recordNo);
            if (sameLocation != null) {
                errors.accept(recordNo, "локация " + format(l) + " уже задана в записи №" + sameLocation
                        + ", локации должны быть уникальны");
            } else {
                runCheck(recordNo, errors, () -> requireUniqueLocation(l));
            }
        });
    }

    private static void runCheck(Integer recordNo, BiConsumer<Integer, String> errors, Runnable check) {
        try {
            check.run();
        } catch (ApiException e) {
            errors.accept(recordNo, e.getMessage());
        }
    }

    /**
     * Имя без пробельных, управляющих и невидимых символов по краям. В таком виде имя сохраняется
     * в БД, поэтому SQL-сравнение {@code LOWER(TRIM(name))} и сравнение в Java совпадают. Обычного
     * {@link String#trim()} мало: он не срезает неразрывный пробел, а SQL {@code TRIM} — табуляцию.
     */
    public static String cleanName(String name) {
        if (name == null) {
            return null;
        }
        int start = 0;
        int end = name.length();
        while (start < end && isInvisible(name.codePointAt(start))) {
            start += Character.charCount(name.codePointAt(start));
        }
        while (end > start && isInvisible(name.codePointBefore(end))) {
            end -= Character.charCount(name.codePointBefore(end));
        }
        return name.substring(start, end);
    }

    private static boolean isInvisible(int codePoint) {
        return Character.isWhitespace(codePoint)
                || Character.isSpaceChar(codePoint)
                || Character.isISOControl(codePoint)
                || Character.getType(codePoint) == Character.FORMAT;
    }

    static String normalizeName(String name) {
        return cleanName(name).toLowerCase(Locale.ROOT);
    }

    private static String nameTaken(String name, Person existing) {
        return "Человек с именем «" + cleanName(name) + "» уже существует (ID " + existing.getId()
                + "), имена должны быть уникальны";
    }

    private static String coordinatesTaken(Coordinates value, Coordinates existing) {
        return "Координаты " + format(value) + " уже существуют (ID " + existing.getId()
                + ") — выберите их из списка существующих";
    }

    private static String locationTaken(Location value, Location existing) {
        return "Локация " + format(value) + " уже существует (ID " + existing.getId()
                + ") — выберите её из списка существующих";
    }

    private static String format(Coordinates c) {
        return "(" + DecimalParser.format(c.getX()) + "; " + DecimalParser.format(c.getY()) + ")";
    }

    private static String format(Location l) {
        return "(" + DecimalParser.format(l.getX()) + "; " + l.getY() + "; " + DecimalParser.format(l.getZ()) + ")";
    }

    // «+ 0.0» превращает -0.0 в 0.0: в SQL они равны, а в equals() у Float/Double — нет
    private record CoordinatesKey(float x, double y) {
        static CoordinatesKey of(Coordinates c) {
            return new CoordinatesKey(c.getX() + 0.0f, c.getY() + 0.0);
        }
    }

    private record LocationKey(double x, long y, double z) {
        static LocationKey of(Location l) {
            return new LocationKey(l.getX() + 0.0, l.getY(), l.getZ() + 0.0);
        }
    }
}
