package itmo.is1.dto;

import java.util.List;

public record ImportHistoryResponse(List<ImportOperationResponse> items, long total, int page, int size,
                                    boolean admin) {
}
