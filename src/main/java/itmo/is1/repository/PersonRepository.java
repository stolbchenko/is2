package itmo.is1.repository;

import itmo.is1.entity.Coordinates;
import itmo.is1.entity.Location;
import itmo.is1.entity.Person;
import itmo.is1.entity.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class PersonRepository {

    private static final Set<String> SORTABLE = Set.of(
            "id", "name", "creationDate", "eyeColor", "hairColor", "height", "nationality");

    @PersistenceContext(unitName = "is1PU")
    private EntityManager em;

    public Person find(Integer id) {
        return em.find(Person.class, id);
    }

    public Person save(Person person) {
        em.persist(person);
        em.flush();
        return person;
    }

    public void saveAll(List<Person> persons) {
        for (Person person : persons) {
            em.persist(person.getCoordinates());
            em.persist(person.getLocation());
            em.persist(person);
            // flush на каждой записи: иначе EclipseLink вставляет строки не в порядке файла
            em.flush();
        }
    }

    public Person update(Person person) {
        Person merged = em.merge(person);
        // UPDATE выполняется сразу: так новая версия объекта (@Version) попадает в ответ клиенту
        em.flush();
        return merged;
    }

    public void delete(Person person) {
        em.remove(em.contains(person) ? person : em.merge(person));
    }

    public List<Person> findPage(PersonFilter filter, String sortField, boolean ascending,
                                 int offset, int limit) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Person> cq = cb.createQuery(Person.class);
        Root<Person> root = cq.from(Person.class);

        List<Predicate> predicates = buildPredicates(cb, root, filter);
        if (!predicates.isEmpty()) {
            cq.where(predicates.toArray(new Predicate[0]));
        }

        Path<?> order = root.get(sortableOrDefault(sortField));
        cq.orderBy(ascending ? cb.asc(order) : cb.desc(order));

        return em.createQuery(cq)
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList();
    }

    public long count(PersonFilter filter) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<Person> root = cq.from(Person.class);
        cq.select(cb.count(root));

        List<Predicate> predicates = buildPredicates(cb, root, filter);
        if (!predicates.isEmpty()) {
            cq.where(predicates.toArray(new Predicate[0]));
        }

        return em.createQuery(cq).getSingleResult();
    }

    public List<Person> findByCoordinates(Integer coordinatesId) {
        return em.createQuery(
                        "SELECT p FROM Person p WHERE p.coordinates.id = :cid", Person.class)
                .setParameter("cid", coordinatesId)
                .getResultList();
    }

    public List<Person> findByLocation(Integer locationId) {
        return em.createQuery(
                        "SELECT p FROM Person p WHERE p.location.id = :lid", Person.class)
                .setParameter("lid", locationId)
                .getResultList();
    }

    public List<Person> findByNormalizedName(String normalizedName) {
        return em.createQuery(
                        "SELECT p FROM Person p WHERE LOWER(TRIM(p.name)) = :name ORDER BY p.id", Person.class)
                .setParameter("name", normalizedName)
                .getResultList();
    }

    public List<Coordinates> findCoordinatesByValue(float x, double y) {
        return em.createQuery(
                        "SELECT c FROM Coordinates c WHERE c.x = :x AND c.y = :y ORDER BY c.id", Coordinates.class)
                .setParameter("x", x)
                .setParameter("y", y)
                .getResultList();
    }

    public List<Location> findLocationsByValue(double x, long y, double z) {
        return em.createQuery(
                        "SELECT l FROM Location l WHERE l.x = :x AND l.y = :y AND l.z = :z ORDER BY l.id",
                        Location.class)
                .setParameter("x", x)
                .setParameter("y", y)
                .setParameter("z", z)
                .getResultList();
    }

    public List<Person> findAll() {
        return em.createQuery("SELECT p FROM Person p", Person.class).getResultList();
    }

    public Coordinates findCoordinates(Integer id) {
        return em.find(Coordinates.class, id);
    }

    public Location findLocation(Integer id) {
        return em.find(Location.class, id);
    }

    public List<Coordinates> allCoordinates() {
        return em.createQuery("SELECT c FROM Coordinates c ORDER BY c.id", Coordinates.class)
                .getResultList();
    }

    public List<Location> allLocations() {
        return em.createQuery("SELECT l FROM Location l ORDER BY l.id", Location.class)
                .getResultList();
    }

    public Coordinates saveCoordinates(Coordinates coordinates) {
        em.persist(coordinates);
        return coordinates;
    }

    public Location saveLocation(Location location) {
        em.persist(location);
        return location;
    }

    public void deleteCoordinates(Coordinates coordinates) {
        em.remove(em.contains(coordinates) ? coordinates : em.merge(coordinates));
    }

    public void deleteLocation(Location location) {
        em.remove(em.contains(location) ? location : em.merge(location));
    }

    private List<Predicate> buildPredicates(CriteriaBuilder cb, Root<Person> root, PersonFilter filter) {
        List<Predicate> predicates = new ArrayList<>();

        if (filter.name() != null && !filter.name().isBlank()) {
            Expression<String> lowered = cb.lower(root.get("name"));
            predicates.add(cb.like(lowered, "%" + filter.name().toLowerCase() + "%"));
        }
        if (filter.eyeColor() != null) {
            predicates.add(cb.equal(root.get("eyeColor"), filter.eyeColor()));
        }
        if (filter.hairColor() != null) {
            predicates.add(cb.equal(root.get("hairColor"), filter.hairColor()));
        }
        if (filter.nationality() != null) {
            predicates.add(cb.equal(root.get("nationality"), filter.nationality()));
        }

        return predicates;
    }

    private String sortableOrDefault(String sortField) {
        return SORTABLE.contains(sortField) ? sortField : "id";
    }

    public User findUserByUsername(String username) {
        try {
            return em.createQuery("SELECT u FROM User u WHERE u.username = :name", User.class)
                    .setParameter("name", username)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    public User saveUser(User user) {
        em.persist(user);
        return user;
    }
}
