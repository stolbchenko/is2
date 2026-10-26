package itmo.is1.service;

import java.util.List;

public class ImportException extends RuntimeException {

    private final List<String> errors;

    public ImportException(String message, List<String> errors) {
        super(message);
        this.errors = List.copyOf(errors);
    }

    public ImportException(String message) {
        this(message, List.of());
    }

    public List<String> getErrors() {
        return errors;
    }
}
