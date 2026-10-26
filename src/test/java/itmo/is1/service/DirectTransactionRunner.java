package itmo.is1.service;

import java.util.function.Supplier;

/** Без JTA: в юнит-тестах сервисов операция просто выполняется. */
class DirectTransactionRunner extends TransactionRunner {

    int calls;

    @Override
    public <T> T serializable(Supplier<T> work) {
        calls++;
        return work.get();
    }
}
