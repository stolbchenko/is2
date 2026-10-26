package itmo.is1.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "coordinates")
public class Coordinates {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @DecimalMax(value = "91", message = "Координата X не может превышать 91")
    @Column(name = "x", nullable = false)
    private float x;

    @NotNull(message = "Координата Y не может быть null")
    @DecimalMax(value = "599", message = "Координата Y не может превышать 599")
    @Column(name = "y", nullable = false)
    private Double y;

    public Coordinates() {
    }

    public Coordinates(float x, Double y) {
        this.x = x;
        this.y = y;
    }

    public Integer getId() {
        return id;
    }

    public float getX() {
        return x;
    }

    public Double getY() {
        return y;
    }
}
