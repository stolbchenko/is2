package itmo.is1.service;

public record PersonChangedEvent(String action, Integer personId) {

    public static PersonChangedEvent created(Integer id) {
        return new PersonChangedEvent("CREATED", id);
    }

    public static PersonChangedEvent updated(Integer id) {
        return new PersonChangedEvent("UPDATED", id);
    }

    public static PersonChangedEvent deleted(Integer id) {
        return new PersonChangedEvent("DELETED", id);
    }

    public static PersonChangedEvent imported() {
        return new PersonChangedEvent("IMPORTED", null);
    }

    public static PersonChangedEvent importFailed() {
        return new PersonChangedEvent("IMPORT_FAILED", null);
    }
}
