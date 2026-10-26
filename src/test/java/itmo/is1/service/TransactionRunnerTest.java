package itmo.is1.service;

import jakarta.persistence.PersistenceException;
import jakarta.transaction.RollbackException;
import jakarta.transaction.Status;
import jakarta.transaction.UserTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionRunnerTest {

    /** Журнал вызовов: begin / isolation / work / commit / rollback / pause. */
    private final List<String> log = new ArrayList<>();
    private final Deque<Exception> commitFailures = new ArrayDeque<>();
    private TransactionRunner runner;

    private class FakeUserTransaction implements UserTransaction {
        private int status = Status.STATUS_NO_TRANSACTION;

        @Override
        public void begin() {
            assertEquals(Status.STATUS_NO_TRANSACTION, status, "вложенная транзакция");
            status = Status.STATUS_ACTIVE;
            log.add("begin");
        }

        @Override
        public void commit() throws RollbackException {
            log.add("commit");
            status = Status.STATUS_NO_TRANSACTION;
            Exception failure = commitFailures.poll();
            if (failure instanceof RollbackException rollback) {
                throw rollback;
            }
        }

        @Override
        public void rollback() {
            log.add("rollback");
            status = Status.STATUS_NO_TRANSACTION;
        }

        @Override
        public void setRollbackOnly() {
            status = Status.STATUS_MARKED_ROLLBACK;
        }

        @Override
        public int getStatus() {
            return status;
        }

        @Override
        public void setTransactionTimeout(int seconds) {
        }
    }

    @BeforeEach
    void setUp() {
        runner = new TransactionRunner() {
            @Override
            void useSerializableIsolation() {
                log.add("isolation");
            }

            @Override
            void pause(int attempt) {
                log.add("pause");
            }
        };
        runner.transaction = new FakeUserTransaction();
    }

    private static PersistenceException sqlFailure(String sqlState) {
        return new PersistenceException("ошибка БД",
                new SQLException("ERROR: could not serialize access", sqlState));
    }

    @Test
    void setsIsolationBeforeWorkAndCommits() {
        String result = runner.serializable(() -> {
            log.add("work");
            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(List.of("begin", "isolation", "work", "commit"), log);
    }

    @Test
    void retriesWholeOperationOnSerializationFailure() {
        AtomicInteger attempts = new AtomicInteger();

        int result = runner.serializable(() -> {
            log.add("work");
            if (attempts.incrementAndGet() < 3) {
                throw sqlFailure("40001");
            }
            return attempts.get();
        });

        assertEquals(3, result);
        assertEquals(List.of(
                "begin", "isolation", "work", "rollback", "pause",
                "begin", "isolation", "work", "rollback", "pause",
                "begin", "isolation", "work", "commit"), log);
    }

    @Test
    void retriesWhenConflictIsDetectedOnCommit() {
        RollbackException onCommit = new RollbackException("ARJUNA016053: Could not commit transaction");
        onCommit.initCause(sqlFailure("40001"));
        commitFailures.add(onCommit);
        AtomicInteger attempts = new AtomicInteger();

        runner.serializable(() -> attempts.incrementAndGet());

        assertEquals(2, attempts.get());
        assertEquals(List.of("begin", "isolation", "commit", "pause", "begin", "isolation", "commit"), log);
    }

    @Test
    void retriesWhenConflictIsInSuppressedExceptionOfRollback() {
        // так Narayana сообщает об отказе PostgreSQL на COMMIT: RollbackException без cause,
        // а LocalXAException → ResourceException → PSQLException(40001) — в suppressed
        RollbackException onCommit = new RollbackException("ARJUNA016053: Could not commit transaction.");
        Exception localXa = new Exception("IJ001156: Could not commit local transaction",
                new Exception("SQLException", new SQLException(
                        "could not serialize access due to read/write dependencies among transactions", "40001")));
        onCommit.addSuppressed(localXa);
        commitFailures.add(onCommit);
        AtomicInteger attempts = new AtomicInteger();

        runner.serializable(() -> attempts.incrementAndGet());

        assertEquals(2, attempts.get());
    }

    @Test
    void cyclicExceptionGraphDoesNotHang() {
        Exception a = new Exception("a");
        Exception b = new Exception("b", a);
        a.addSuppressed(b);
        assertFalse(TransactionRunner.isRetryable(a));
    }

    @Test
    void retriesOnDeadlockFoundInChainedSqlException() {
        SQLException batch = new SQLException("Batch entry 0 was aborted", "XX000");
        batch.setNextException(new SQLException("deadlock detected", "40P01"));
        AtomicInteger attempts = new AtomicInteger();

        runner.serializable(() -> {
            if (attempts.incrementAndGet() == 1) {
                throw new PersistenceException(batch);
            }
            return null;
        });

        assertEquals(2, attempts.get());
    }

    @Test
    void businessErrorIsNotRetriedAndRollsBack() {
        ApiException conflict = ApiException.conflict("name", "имя занято");
        AtomicInteger attempts = new AtomicInteger();

        ApiException thrown = assertThrows(ApiException.class, () -> runner.serializable(() -> {
            attempts.incrementAndGet();
            throw conflict;
        }));

        assertSame(conflict, thrown);
        assertEquals(1, attempts.get());
        assertEquals(List.of("begin", "isolation", "rollback"), log);
    }

    @Test
    void otherDatabaseErrorsAreNotRetried() {
        PersistenceException uniqueViolation = sqlFailure("23505");
        AtomicInteger attempts = new AtomicInteger();

        assertSame(uniqueViolation, assertThrows(PersistenceException.class, () -> runner.serializable(() -> {
            attempts.incrementAndGet();
            throw uniqueViolation;
        })));
        assertEquals(1, attempts.get());
    }

    @Test
    void givesUpAfterMaxAttemptsWithServiceUnavailable() {
        AtomicInteger attempts = new AtomicInteger();

        ApiException e = assertThrows(ApiException.class, () -> runner.serializable(() -> {
            attempts.incrementAndGet();
            throw sqlFailure("40001");
        }));

        assertEquals(503, e.getStatus());
        assertEquals(TransactionRunner.MAX_ATTEMPTS, attempts.get());
        assertEquals(TransactionRunner.MAX_ATTEMPTS, log.stream().filter("rollback"::equals).count());
        assertFalse(log.contains("commit"));
    }

    @Test
    void recognisesRetryableErrorsOnlyBySqlState() {
        assertTrue(TransactionRunner.isRetryable(new RuntimeException(sqlFailure("40001"))));
        assertTrue(TransactionRunner.isRetryable(sqlFailure("40P01")));
        assertFalse(TransactionRunner.isRetryable(sqlFailure("23503")));
        assertFalse(TransactionRunner.isRetryable(new IllegalStateException("без причины")));
    }

    @Test
    void optimisticLockFailureIsRetried_soTheNextAttemptAnswers409ByVersionCheck() {
        assertTrue(TransactionRunner.isRetryable(new jakarta.persistence.OptimisticLockException("устарела")));
        assertTrue(TransactionRunner.isRetryable(new IllegalStateException("коммит",
                new jakarta.persistence.RollbackException(new jakarta.persistence.OptimisticLockException()))));
    }

    @Test
    void backoffGrowsExponentiallyUpToLimit() {
        assertEquals(10, TransactionRunner.backoffCeiling(1));
        assertEquals(20, TransactionRunner.backoffCeiling(2));
        assertEquals(320, TransactionRunner.backoffCeiling(6));
        assertEquals(TransactionRunner.MAX_PAUSE_MILLIS, TransactionRunner.backoffCeiling(7));
        assertEquals(TransactionRunner.MAX_PAUSE_MILLIS, TransactionRunner.backoffCeiling(TransactionRunner.MAX_ATTEMPTS));
    }

    @Test
    void runnableOverloadUsesSameTransaction() {
        runner.serializable(() -> log.add("work"));
        assertEquals(List.of("begin", "isolation", "work", "commit"), log);
    }
}
