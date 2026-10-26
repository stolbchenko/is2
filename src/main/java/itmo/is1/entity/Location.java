package itmo.is1.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "location")
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotNull(message = "Координата X локации не может быть null")
    @Column(name = "x", nullable = false)
    private Double x;

    @Column(name = "y", nullable = false)
    private long y;

    @NotNull(message = "Координата Z локации не может быть null")
    @Column(name = "z", nullable = false)
    private Double z;

    public Location() {
    }

    public Location(Double x, long y, Double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Integer getId() {
        return id;
    }

    public Double getX() {
        return x;
    }

    public long getY() {
        return y;
    }

    public Double getZ() {
        return z;
    }
}
