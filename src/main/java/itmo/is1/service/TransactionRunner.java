package itmo.is1.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Status;
import jakarta.transaction.SystemException;
import jakarta.transaction.UserTransaction;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Изменяющие операции в SERIALIZABLE с повтором. Их проверки «прочитал → записал» (уникальность,
 * существование объекта, зависимые объекты) в READ COMMITTED не защищены от параллельных транзакций.
 * Ошибка сериализации 40001 — штатный исход: операция повторяется и видит результат конкурента.
 * Так же повторяется OptimisticLockException: в новой попытке проверка версии ответит 409.
 */
@ApplicationScoped
public class TransactionRunner {

    /**
     * На маленьких таблицах PostgreSQL сканирует их целиком и ставит предикатную блокировку на всю
     * таблицу, поэтому конфликтуют даже операции над разными объектами (ложные срабатывания SSI).
     * Под нагрузкой из 10–20 одновременных пользователей операции нужно до десятка с лишним повторов.
     */
    static final int MAX_ATTEMPTS = 20;

    /** Верхняя граница задержки между попытками, мс. */
    static final long MAX_PAUSE_MILLIS = 500;

    /** 40001 — serialization_failure, 40P01 — deadlock_detected: обе безопасно повторять. */
    private static final Set<String> RETRYABLE_SQL_STATES = Set.of("40001", "40P01");

    /** jakarta.persistence.OptimisticLockException и одноимённое исключение EclipseLink. */
    private static final String OPTIMISTIC_LOCK = "OptimisticLockException";

    private static final Logger LOG = Logger.getLogger(TransactionRunner.class.getName());

    @Inject
    UserTransaction transaction;

    @PersistenceContext(unitName = "is1PU")
    EntityManager em;

    public <T> T serializable(Supplier<T> work) {
        for (int attempt = 1; ; attempt++) {
            try {
                return runOnce(work);
            } catch (RuntimeException e) {
                if (!isRetryable(e)) {
                    throw e;
                }
                if (attempt >= MAX_ATTEMPTS) {
                    LOG.warning("Операция не выполнена за " + MAX_ATTEMPTS
                            + " попыток из-за конфликтов параллельных транзакций");
                    throw ApiException.concurrencyFailure(
                            "Операция конфликтует с одновременными изменениями других пользователей, "
                                    + "повторите попытку");
                }
                int next = attempt + 1;
                LOG.fine(() -> "Конфликт сериализации, повтор операции, попытка " + next);
                pause(attempt);
            }
        }
    }

    public void serializable(Runnable work) {
        serializable(() -> {
            work.run();
            return null;
        });
    }

    private <T> T runOnce(Supplier<T> work) {
        begin();
        boolean committed = false;
        try {
            useSerializableIsolation();
            T result = work.get();
            commit();
            committed = true;
            return result;
        } finally {
            if (!committed) {
                rollbackQuietly();
            }
        }
    }

    /**
     * Должно быть первым SQL-выражением транзакции: после первого запроса PostgreSQL
     * уровень изоляции уже не меняет. Действует только до конца этой транзакции,
     * соединение возвращается в пул с уровнем по умолчанию.
     */
    void useSerializableIsolation() {
        em.createNativeQuery("SET TRANSACTION ISOLATION LEVEL SERIALIZABLE").executeUpdate();
    }

    void pause(int attempt) {
        // экспоненциальная задержка со случайным разбросом («full jitter»): повторы конкурентов
        // расходятся во времени и с каждой попыткой реже пересекаются снова
        long millis = ThreadLocalRandom.current().nextLong(1, backoffCeiling(attempt) + 1);
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Повтор операции прерван", e);
        }
    }

    static long backoffCeiling(int attempt) {
        return Math.min(MAX_PAUSE_MILLIS, 10L << Math.min(attempt - 1, 10));
    }

    /**
     * Ищет SQLSTATE 40001/40P01 или OptimisticLockException во всём графе исключения: и по цепочке
     * cause, и среди suppressed — Narayana при сбое однофазного коммита кладёт причину
     * (LocalXAException → PSQLException) именно в suppressed у RollbackException.
     */
    static boolean isRetryable(Throwable error) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<Throwable> pending = new ArrayDeque<>();
        pending.push(error);
        while (!pending.isEmpty()) {
            Throwable t = pending.pop();
            if (!visited.add(t)) {
                continue;
            }
            if (OPTIMISTIC_LOCK.equals(t.getClass().getSimpleName())) {
                return true;
            }
            if (t instanceof SQLException sql) {
                for (SQLException e = sql; e != null; e = e.getNextException()) {
                    if (RETRYABLE_SQL_STATES.contains(e.getSQLState())) {
                        return true;
                    }
                }
            }
            if (t.getCause() != null) {
                pending.push(t.getCause());
            }
            for (Throwable suppressed : t.getSuppressed()) {
                pending.push(suppressed);
            }
        }
        return false;
    }

    private void begin() {
        try {
            transaction.begin();
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось начать транзакцию", e);
        }
    }

    private void commit() {
        try {
            transaction.commit();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            // RollbackException и т. п.: причина (в т. ч. ошибка сериализации) — в цепочке cause
            throw new TransactionFailedException(e);
        }
    }

    private void rollbackQuietly() {
        try {
            if (transaction.getStatus() != Status.STATUS_NO_TRANSACTION) {
                transaction.rollback();
            }
        } catch (SystemException | RuntimeException e) {
            LOG.warning("Не удалось откатить транзакцию: " + e);
        }
    }

    static class TransactionFailedException extends RuntimeException {
        TransactionFailedException(Throwable cause) {
            super("Транзакция не зафиксирована: " + cause.getMessage(), cause);
        }
    }
}
