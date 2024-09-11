package de.tum.cit.aet.artemis.domain.competency;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.domain.enumeration.CompetencyProgressConfidenceReason;

/**
 * This class models the 'progress' association between a user and a competency.
 */
@Entity
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@Table(name = "competency_user")
@EntityListeners(AuditingEntityListener.class)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class CompetencyProgress implements Serializable {

    /**
     * The primary key of the association, composited through {@link CompetencyUserId}.
     */
    @EmbeddedId
    @JsonIgnore
    private CompetencyUserId id = new CompetencyUserId();

    @ManyToOne
    @MapsId("userId")
    @JsonIgnore
    private User user;

    @ManyToOne
    @MapsId("competencyId")
    @JsonIgnore
    private CourseCompetency competency;

    @Column(name = "progress")
    private Double progress;

    @Column(name = "confidence")
    private Double confidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "confidence_reason", columnDefinition = "varchar(30) default 'NO_REASON'")
    private CompetencyProgressConfidenceReason confidenceReason = CompetencyProgressConfidenceReason.NO_REASON;

    @LastModifiedDate
    @Column(name = "last_modified_date")
    @JsonIgnore
    private Instant lastModifiedDate;

    public CompetencyUserId getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public CourseCompetency getCompetency() {
        return competency;
    }

    public void setCompetency(CourseCompetency competency) {
        this.competency = competency;
    }

    public Double getProgress() {
        return progress;
    }

    public void setProgress(Double progress) {
        this.progress = progress;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public CompetencyProgressConfidenceReason getConfidenceReason() {
        return confidenceReason;
    }

    public void setConfidenceReason(CompetencyProgressConfidenceReason confidenceReason) {
        this.confidenceReason = confidenceReason;
    }

    public Instant getLastModifiedDate() {
        return lastModifiedDate;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        CompetencyProgress that = (CompetencyProgress) obj;
        return getId().equals(that.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "CompetencyProgress{" + "id=" + id + ", user=" + user + ", competency=" + competency + ", progress=" + progress + ", confidence=" + confidence + '}';
    }

    /**
     * This class is used to create a composite primary key (user_id, competency_id).
     * See also <a href="https://www.baeldung.com/spring-jpa-embedded-method-parameters">...</a>
     */
    @Embeddable
    public static class CompetencyUserId implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private Long userId;

        private Long competencyId;

        public CompetencyUserId() {
            // Empty constructor for Spring
        }

        public CompetencyUserId(Long userId, Long competencyId) {
            this.userId = userId;
            this.competencyId = competencyId;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            CompetencyUserId that = (CompetencyUserId) obj;
            if (userId == null || that.userId == null) {
                return false;
            }
            return userId.equals(that.userId) && competencyId.equals(that.competencyId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, competencyId);
        }
    }
}
