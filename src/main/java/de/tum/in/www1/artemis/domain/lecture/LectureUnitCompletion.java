package de.tum.in.www1.artemis.domain.lecture;

import java.io.Serial;
import java.io.Serializable;
import java.time.ZonedDateTime;

import javax.persistence.*;

import de.tum.in.www1.artemis.domain.User;

@Entity
@Table(name = "lecture_unit_user")
public class LectureUnitCompletion {

    @EmbeddedId
    private LectureUnitUserId id = new LectureUnitUserId();

    @ManyToOne
    @MapsId("userId")
    private User user;

    @ManyToOne
    @MapsId("lectureUnitId")
    private LectureUnit lectureUnit;

    @Column(name = "completed_date")
    private ZonedDateTime completedAt;

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

    @Embeddable
    public static class LectureUnitUserId implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private Long userId;

        private Long lectureUnitId;
    }

}
