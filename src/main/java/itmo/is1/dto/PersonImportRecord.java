package itmo.is1.dto;

import itmo.is1.dto.PersonRequest.CoordinatesRequest;
import itmo.is1.dto.PersonRequest.LocationRequest;
import itmo.is1.entity.Color;
import itmo.is1.entity.Country;

/** Запись файла импорта: координаты и локация задаются значениями, ссылки по ID не допускаются. */
public record PersonImportRecord(
        String name,
        CoordinatesRequest coordinates,
        Color eyeColor,
        Color hairColor,
        LocationRequest location,
        String height,
        Country nationality
) {
}
