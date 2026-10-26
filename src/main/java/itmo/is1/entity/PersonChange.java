package itmo.is1.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;

/** Запись журнала. Ссылается на объект числом personId без внешнего ключа, чтобы пережить его удаление. */
@Entity
@Table(name = "person_change")
public class PersonChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "person_id", nullable = false)
    private Integer personId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private ChangeAction action;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "actor_id", nullable = false)
    private User actor;

    /** Владелец объекта на момент изменения — по нему определяется, кто видит запись. */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @Column(name = "before_state")
    private String beforeState;

    @Column(name = "after_state")
    private String afterState;

    @Column(name = "note")
    private String note;

    protected PersonChange() {
    }

    public PersonChange(Integer personId, ChangeAction action, User actor, User owner,
                        String beforeState, String afterState, String note) {
        this.personId = personId;
        this.action = action;
        this.actor = actor;
        this.owner = owner;
        this.createdAt = ZonedDateTime.now();
        this.beforeState = beforeState;
        this.afterState = afterState;
        this.note = note;
    }

    public Integer getId() {
        return id;
    }

    public Integer getPersonId() {
        return personId;
    }

    public ChangeAction getAction() {
        return action;
    }

    public User getActor() {
        return actor;
    }

    public User getOwner() {
        return owner;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public String getBeforeState() {
        return beforeState;
    }

    public String getAfterState() {
        return afterState;
    }

    public String getNote() {
        return note;
    }
}
