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

@Entity
@Table(name = "import_operation")
public class ImportOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ImportStatus status;

    @Column(name = "added_count")
    private Integer addedCount;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "started_at", nullable = false)
    private ZonedDateTime startedAt;

    @Column(name = "error_message")
    private String errorMessage;

    protected ImportOperation() {
    }

    private ImportOperation(User user, ImportStatus status, Integer addedCount, String fileName,
                            ZonedDateTime startedAt, String errorMessage) {
        this.user = user;
        this.status = status;
        this.addedCount = addedCount;
        this.fileName = fileName;
        this.startedAt = startedAt;
        this.errorMessage = errorMessage;
    }

    public static ImportOperation success(User user, int addedCount, String fileName, ZonedDateTime startedAt) {
        return new ImportOperation(user, ImportStatus.SUCCESS, addedCount, fileName, startedAt, null);
    }

    public static ImportOperation failure(User user, String errorMessage, String fileName, ZonedDateTime startedAt) {
        return new ImportOperation(user, ImportStatus.FAILED, null, fileName, startedAt, errorMessage);
    }

    public Integer getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public ImportStatus getStatus() {
        return status;
    }

    public Integer getAddedCount() {
        return addedCount;
    }

    public String getFileName() {
        return fileName;
    }

    public ZonedDateTime getStartedAt() {
        return startedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
