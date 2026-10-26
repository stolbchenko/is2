package itmo.is1.dto;

import itmo.is1.entity.Color;
import itmo.is1.entity.Country;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PersonRequest(
        @NotBlank(message = "Имя не может быть пустым")
        String name,

        Integer coordinatesId,
        CoordinatesRequest coordinates,

        Color eyeColor,

        @NotNull(message = "Цвет волос не может быть null")
        Color hairColor,

        Integer locationId,
        LocationRequest location,

        @NotBlank(message = "Рост не может быть пустым")
        String height,

        Country nationality,

        /** Версия объекта, которую видел клиент; обязательна при изменении, при создании не нужна. */
        Integer version
) {

    public record CoordinatesRequest(String x, String y) {
    }

    public record LocationRequest(String x, String y, String z) {
    }
}
