package itmo.is1.service;

import jakarta.ws.rs.core.Response;

public class ApiException extends RuntimeException {

    private final String field;
    private final int status;

    private ApiException(String field, String message, int status) {
        super(message);
        this.field = field;
        this.status = status;
    }

    public static ApiException validation(String field, String message) {
        boolean notFound = message != null && message.contains("не найден");
        int status = notFound
                ? Response.Status.NOT_FOUND.getStatusCode()
                : Response.Status.BAD_REQUEST.getStatusCode();
        return new ApiException(field, message, status);
    }

    public static ApiException conflict(String field, String message) {
        return new ApiException(field, message, Response.Status.CONFLICT.getStatusCode());
    }

    public static ApiException concurrencyFailure(String message) {
        return new ApiException(null, message, Response.Status.SERVICE_UNAVAILABLE.getStatusCode());
    }

    public static ApiException accessDenied(String message) {
        return new ApiException(null, message, Response.Status.FORBIDDEN.getStatusCode());
    }

    public String getField() {
        return field;
    }

    public int getStatus() {
        return status;
    }
}
