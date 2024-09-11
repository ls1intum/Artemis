package de.tum.cit.aet.artemis.domain.lecture;

import java.io.Serial;
import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;

import de.tum.cit.aet.artemis.domain.User;

/**
 * This class models the 'completion' association between a user and a lecture unit.
 * Because we track some metadata (e.g., the completion date), this can not be modeled as a simple JPA relationship.
 */
@Entity
@Table(name = "lecture_unit_user")
public class LectureUnitCompletion {

    /**
     * The primary key of the association, composited through {@link LectureUnitUserId}.
     */
    @EmbeddedId
    @JsonIgnore
    private LectureUnitUserId id = new LectureUnitUserId();

    @ManyToOne
    @MapsId("userId")
    @JsonIgnore
    private User user;

    @ManyToOne
    @MapsId("lectureUnitId")
    @JsonIgnore
    private LectureUnit lectureUnit;

    @Column(name = "completed_date")
    private ZonedDateTime completedAt;

    public LectureUnitUserId getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public LectureUnit getLectureUnit() {
        return lectureUnit;
    }

    public void setLectureUnit(LectureUnit lectureUnit) {
        this.lectureUnit = lectureUnit;
    }

    public ZonedDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(ZonedDateTime completedAt) {
        this.completedAt = completedAt;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        LectureUnitCompletion that = (LectureUnitCompletion) obj;
        return getId().equals(that.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    /**
     * This class is used to create a composite primary key (user_id, lecture_unit_id).
     * See also <a href="https://www.baeldung.com/spring-jpa-embedded-method-parameters">...</a>
     */
    @Embeddable
    @SuppressWarnings("unused")
    public static class LectureUnitUserId implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private Long userId;

        private Long lectureUnitId;

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            LectureUnitUserId that = (LectureUnitUserId) obj;
            return userId.equals(that.userId) && lectureUnitId.equals(that.lectureUnitId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, lectureUnitId);
        }
    }

}
