package itmo.is1.service;

/** К какому состоянию из записи журнала откатывать: до изменения или после него. */
public enum RollbackTarget {
    BEFORE,
    AFTER
}
