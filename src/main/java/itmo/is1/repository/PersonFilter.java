package itmo.is1.repository;

import itmo.is1.entity.Color;
import itmo.is1.entity.Country;

public record PersonFilter(String name, Color eyeColor, Color hairColor, Country nationality) {
}
