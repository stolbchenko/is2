package itmo.is1.dto;

import itmo.is1.dto.PersonState.FieldChange;
import itmo.is1.entity.ChangeAction;
import itmo.is1.entity.PersonChange;

import java.time.format.DateTimeFormatter;
import java.util.List;

public record PersonChangeResponse(
        Integer id,
        ChangeAction action,
        Integer personId,
        String actor,
        String owner,
        String createdAt,
        List<FieldChange> changes,
        boolean hasBefore,
        boolean hasAfter,
        String note
) {

    public static PersonChangeResponse of(PersonChange change) {
        PersonState before = change.getBeforeState() == null ? null : PersonState.fromJson(change.getBeforeState());
        PersonState after = change.getAfterState() == null ? null : PersonState.fromJson(change.getAfterState());
        return new PersonChangeResponse(
                change.getId(),
                change.getAction(),
                change.getPersonId(),
                change.getActor().getUsername(),
                change.getOwner().getUsername(),
                change.getCreatedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                PersonState.diff(before, after),
                before != null,
                after != null,
                change.getNote());
    }
}
