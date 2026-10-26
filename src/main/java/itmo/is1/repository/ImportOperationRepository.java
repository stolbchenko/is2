package itmo.is1.repository;

import itmo.is1.entity.ImportOperation;
import itmo.is1.entity.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

import java.util.List;

@ApplicationScoped
public class ImportOperationRepository {

    @PersistenceContext(unitName = "is1PU")
    private EntityManager em;

    public ImportOperation save(ImportOperation operation) {
        em.persist(operation);
        // id нужен сразу — для ответа клиенту
        em.flush();
        return operation;
    }

    /** Страница истории, новые операции первыми; {@code user == null} — операции всех пользователей. */
    public List<ImportOperation> findPage(User user, int offset, int limit) {
        TypedQuery<ImportOperation> query = user == null
                ? em.createQuery("SELECT o FROM ImportOperation o ORDER BY o.id DESC", ImportOperation.class)
                : em.createQuery("SELECT o FROM ImportOperation o WHERE o.user = :user ORDER BY o.id DESC",
                        ImportOperation.class).setParameter("user", user);
        return query.setFirstResult(offset).setMaxResults(limit).getResultList();
    }

    public long count(User user) {
        TypedQuery<Long> query = user == null
                ? em.createQuery("SELECT COUNT(o) FROM ImportOperation o", Long.class)
                : em.createQuery("SELECT COUNT(o) FROM ImportOperation o WHERE o.user = :user", Long.class)
                        .setParameter("user", user);
        return query.getSingleResult();
    }
}
