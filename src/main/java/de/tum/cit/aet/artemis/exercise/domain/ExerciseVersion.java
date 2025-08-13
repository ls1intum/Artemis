package de.tum.cit.aet.artemis.exercise.domain;

import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import de.tum.cit.aet.artemis.core.domain.AbstractAuditingEntity;
import de.tum.cit.aet.artemis.core.domain.User;

@Entity
@Table(name = "exercise_version")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class ExerciseVersion extends AbstractAuditingEntity {

    /**
     * Enumeration for different types of exercise versions.
     */
    public enum VersionType {
        /**
         * Complete snapshot of the exercise state.
         * Used for the first version and periodically for optimization.
         */
        FULL_SNAPSHOT,

        /**
         * Contains only the changes from the previous version.
         * More storage-efficient for tracking incremental changes.
         */
        INCREMENTAL_DIFF
    }

    @ManyToOne
    @JoinColumn(name = "exercise_id")
    private Exercise exercise;

    @ManyToOne
    @JoinColumn(name = "author_id")
    private User author;

    @Enumerated(EnumType.STRING)
    @Column(name = "version_type", nullable = false)
    private VersionType versionType;

    @ManyToOne
    @JoinColumn(name = "previous_version_id")
    private ExerciseVersion previousVersion;

    /**
     * This field contains either a complete exercise state (FULL_SNAPSHOT) or
     * only the changes from the previous version (INCREMENTAL_DIFF).
     * Direct JSON storage without custom converter for better performance.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content_json", columnDefinition = "json", nullable = false)
    private Map<String, Object> content;

    /**
     * SHA-256 hash of the content for integrity verification.
     */
    @Column(name = "content_hash", length = 64)
    private String contentHash;

    public Exercise getExercise() {
        return exercise;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    public User getAuthor() {
        return author;
    }

    public void setAuthor(User author) {
        this.author = author;
    }

    public VersionType getVersionType() {
        return versionType;
    }

    public void setVersionType(VersionType versionType) {
        this.versionType = versionType;
    }

    public ExerciseVersion getPreviousVersion() {
        return previousVersion;
    }

    public void setPreviousVersion(ExerciseVersion previousVersion) {
        this.previousVersion = previousVersion;
    }

    public Map<String, Object> getContent() {
        return content;
    }

    public void setContent(Map<String, Object> content) {
        this.content = content;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }
}
