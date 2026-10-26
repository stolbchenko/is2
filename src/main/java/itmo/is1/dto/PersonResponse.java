package itmo.is1.dto;

import itmo.is1.entity.Color;
import itmo.is1.entity.Coordinates;
import itmo.is1.entity.Country;
import itmo.is1.entity.Location;
import itmo.is1.entity.Person;

import java.time.format.DateTimeFormatter;

public record PersonResponse(
        Integer id,
        String name,
        CoordinatesResponse coordinates,
        String creationDate,
        Color eyeColor,
        Color hairColor,
        LocationResponse location,
        String height,
        Country nationality,
        String owner,
        boolean editable,
        Integer version
) {

    public static PersonResponse of(Person person, String currentUser, boolean admin) {
        String owner = person.getOwner().getUsername();
        return new PersonResponse(
                person.getId(),
                person.getName(),
                CoordinatesResponse.of(person.getCoordinates()),
                person.getCreationDate().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                person.getEyeColor(),
                person.getHairColor(),
                LocationResponse.of(person.getLocation()),
                DecimalParser.format(person.getHeight()),
                person.getNationality(),
                owner,
                admin || owner.equals(currentUser),
                person.getVersion());
    }

    public record CoordinatesResponse(Integer id, String x, String y) {

        public static CoordinatesResponse of(Coordinates coordinates) {
            return new CoordinatesResponse(
                    coordinates.getId(),
                    DecimalParser.format(coordinates.getX()),
                    DecimalParser.format(coordinates.getY()));
        }
    }

    public record LocationResponse(Integer id, String x, String y, String z) {

        public static LocationResponse of(Location location) {
            return new LocationResponse(
                    location.getId(),
                    DecimalParser.format(location.getX()),
                    DecimalParser.format(location.getY()),
                    DecimalParser.format(location.getZ()));
        }
    }
}
