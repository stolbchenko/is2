package itmo.is1.dto;

import java.util.List;

public record ChangeHistoryResponse(List<PersonChangeResponse> items, long total, int page, int size,
                                    boolean admin) {
}
