package itmo.is1.dto;

import itmo.is1.entity.ImportOperation;
import itmo.is1.entity.ImportStatus;

import java.time.format.DateTimeFormatter;

public record ImportOperationResponse(
        Integer id,
        ImportStatus status,
        String username,
        Integer addedCount,
        String fileName,
        String startedAt,
        String errorMessage
) {

    public static ImportOperationResponse of(ImportOperation operation) {
        return new ImportOperationResponse(
                operation.getId(),
                operation.getStatus(),
                operation.getUser().getUsername(),
                operation.getAddedCount(),
                operation.getFileName(),
                operation.getStartedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                operation.getErrorMessage());
    }
}
