package itmo.is1.repository;

import itmo.is1.entity.PersonChange;
import itmo.is1.entity.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class PersonChangeRepository {

    @PersistenceContext(unitName = "is1PU")
    private EntityManager em;

    public PersonChange save(PersonChange change) {
        em.persist(change);
        // flush сразу, чтобы записи шли в БД в порядке изменений и получали возрастающие id
        em.flush();
        return change;
    }

    public PersonChange find(Integer id) {
        return em.find(PersonChange.class, id);
    }

    /**
     * Страница журнала, новые записи первыми. {@code owner == null} — записи по объектам всех
     * владельцев; {@code personId == null} — по всем объектам.
     */
    public List<PersonChange> findPage(User owner, Integer personId, int offset, int limit) {
        TypedQuery<PersonChange> query = em.createQuery(
                "SELECT c FROM PersonChange c" + where(owner, personId) + " ORDER BY c.id DESC", PersonChange.class);
        bind(query, owner, personId);
        return query.setFirstResult(offset).setMaxResults(limit).getResultList();
    }

    public long count(User owner, Integer personId) {
        TypedQuery<Long> query = em.createQuery(
                "SELECT COUNT(c) FROM PersonChange c" + where(owner, personId), Long.class);
        bind(query, owner, personId);
        return query.getSingleResult();
    }

    private static String where(User owner, Integer personId) {
        List<String> conditions = new ArrayList<>();
        if (owner != null) {
            conditions.add("c.owner = :owner");
        }
        if (personId != null) {
            conditions.add("c.personId = :personId");
        }
        return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
    }

    private static void bind(TypedQuery<?> query, User owner, Integer personId) {
        if (owner != null) {
            query.setParameter("owner", owner);
        }
        if (personId != null) {
            query.setParameter("personId", personId);
        }
    }
}
