package de.tum.in.www1.artemis.domain.competency;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.User;

/**
 * This class models the 'progress' association between a user and a competency.
 */
@Entity
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@Table(name = "learning_goal_user")
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
    @MapsId("learningGoalId")
    @JsonIgnore
    private Competency learningGoal;

    @Column(name = "progress")
    private Double progress;

    @Column(name = "confidence")
    private Double confidence;

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

    public Competency getCompetency() {
        return learningGoal;
    }

    public void setCompetency(Competency competency) {
        this.learningGoal = competency;
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
        return "CompetencyProgress{" + "id=" + id + ", user=" + user + ", competency=" + learningGoal + ", progress=" + progress + ", confidence=" + confidence + '}';
    }

    /**
     * This class is used to create a composite primary key (user_id, learning_goal_id).
     * See also https://www.baeldung.com/spring-jpa-embedded-method-parameters
     */
    @Embeddable
    @SuppressWarnings("unused")
    public static class CompetencyUserId implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private Long userId;

        private Long learningGoalId;

        public CompetencyUserId() {
            // Empty constructor for Spring
        }

        public CompetencyUserId(Long userId, Long learningGoalId) {
            this.userId = userId;
            this.learningGoalId = learningGoalId;
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
            return userId.equals(that.userId) && learningGoalId.equals(that.learningGoalId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, learningGoalId);
        }
    }
}
