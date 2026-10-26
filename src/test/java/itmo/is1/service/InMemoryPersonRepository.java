package itmo.is1.service;

import itmo.is1.entity.Color;
import itmo.is1.entity.Coordinates;
import itmo.is1.entity.Location;
import itmo.is1.entity.Person;
import itmo.is1.entity.User;
import itmo.is1.repository.PersonRepository;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Репозиторий в памяти для юнит-тестов бизнес-логики без БД и контейнера. */
class InMemoryPersonRepository extends PersonRepository {

    final List<Person> persons = new ArrayList<>();
    final List<Coordinates> coordinates = new ArrayList<>();
    final List<Location> locations = new ArrayList<>();
    final List<User> users = new ArrayList<>();
    int saveAllCalls;
    private int nextId = 1;

    static void setId(Object entity, int id) {
        setField(entity, "id", id);
    }

    /** Для полей без сеттера (id из БД, роль, назначаемая SQL-запросом). */
    static void setField(Object entity, String name, Object value) {
        try {
            Field field = entity.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(entity, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private <T> T withId(T entity) {
        setId(entity, nextId++);
        return entity;
    }

    User addUser(String username) {
        User user = new User();
        user.setUsername(username);
        users.add(withId(user));
        return user;
    }

    Coordinates addCoordinates(float x, double y) {
        return saveCoordinates(new Coordinates(x, y));
    }

    Location addLocation(double x, long y, double z) {
        return saveLocation(new Location(x, y, z));
    }

    Person addPerson(String name, User owner) {
        Person person = new Person();
        person.setName(name);
        person.setOwner(owner);
        person.setHeight(170f);
        person.setHairColor(Color.BLACK);
        person.setCoordinates(addCoordinates(persons.size() + 10, persons.size() + 10));
        person.setLocation(addLocation(persons.size() + 10, persons.size() + 10, persons.size() + 10));
        return save(person);
    }

    @Override
    public User findUserByUsername(String username) {
        return users.stream().filter(u -> u.getUsername().equals(username)).findFirst().orElse(null);
    }

    @Override
    public Person find(Integer id) {
        return persons.stream().filter(p -> p.getId().equals(id)).findFirst().orElse(null);
    }

    /** Как {@code LOWER(TRIM(p.name))} в PostgreSQL: TRIM срезает только пробелы, но не табуляцию и т. п. */
    @Override
    public List<Person> findByNormalizedName(String normalizedName) {
        return persons.stream()
                .filter(p -> sqlTrim(p.getName()).toLowerCase(Locale.ROOT).equals(normalizedName))
                .toList();
    }

    private static String sqlTrim(String value) {
        return value.replaceAll("^ +| +$", "");
    }

    // == как в SQL: -0.0 и 0.0 равны
    @Override
    public List<Coordinates> findCoordinatesByValue(float x, double y) {
        return coordinates.stream().filter(c -> c.getX() == x && c.getY() == y).toList();
    }

    @Override
    public List<Location> findLocationsByValue(double x, long y, double z) {
        return locations.stream().filter(l -> l.getX() == x && l.getY() == y && l.getZ() == z).toList();
    }

    @Override
    public Coordinates findCoordinates(Integer id) {
        return coordinates.stream().filter(c -> c.getId().equals(id)).findFirst().orElse(null);
    }

    @Override
    public Location findLocation(Integer id) {
        return locations.stream().filter(l -> l.getId().equals(id)).findFirst().orElse(null);
    }

    /** Версия (@Version) — как у EclipseLink: 1 при вставке, +1 при каждом изменении. */
    @Override
    public Person save(Person person) {
        setField(person, "version", 1);
        persons.add(withId(person));
        return person;
    }

    @Override
    public Person update(Person person) {
        setField(person, "version", person.getVersion() + 1);
        return person;
    }

    @Override
    public void delete(Person person) {
        persons.remove(person);
    }

    @Override
    public List<Person> findByCoordinates(Integer coordinatesId) {
        return persons.stream().filter(p -> p.getCoordinates().getId().equals(coordinatesId)).toList();
    }

    @Override
    public List<Person> findByLocation(Integer locationId) {
        return persons.stream().filter(p -> p.getLocation().getId().equals(locationId)).toList();
    }

    @Override
    public void deleteCoordinates(Coordinates value) {
        coordinates.remove(value);
    }

    @Override
    public void deleteLocation(Location value) {
        locations.remove(value);
    }

    @Override
    public Coordinates saveCoordinates(Coordinates value) {
        coordinates.add(withId(value));
        return value;
    }

    @Override
    public Location saveLocation(Location value) {
        locations.add(withId(value));
        return value;
    }

    @Override
    public void saveAll(List<Person> batch) {
        saveAllCalls++;
        for (Person person : batch) {
            saveCoordinates(person.getCoordinates());
            saveLocation(person.getLocation());
            save(person);
        }
    }
}
