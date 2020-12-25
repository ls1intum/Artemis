package de.tum.in.www1.artemis.domain;

import java.io.Serializable;

import javax.persistence.*;

import org.hibernate.annotations.Immutable;

import com.fasterxml.jackson.annotation.JsonInclude;

@Entity
@Table(name = "view_teams_of_course")
@Immutable
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class CourseTeamView {

    @EmbeddedId
    private CourseTeamViewId courseTeamViewId;

    public CourseTeamView(CourseTeamViewId courseTeamViewId) {
        this.courseTeamViewId = courseTeamViewId;
    }

    public CourseTeamView() {
    }

    @Immutable
    @Embeddable
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class CourseTeamViewId implements Serializable {

        @Column(name = "COURSE_ID")
        private long course_id;

        @Column(name = "TEAM_ID")
        private long teamId;

        public CourseTeamViewId(long course_id, long teamId) {
            this.course_id = course_id;
            this.teamId = teamId;
        }

        public CourseTeamViewId() {
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;

            CourseTeamViewId that = (CourseTeamViewId) o;

            if (course_id != that.course_id)
                return false;
            return teamId == that.teamId;
        }

        @Override
        public int hashCode() {
            int result = (int) (course_id ^ (course_id >>> 32));
            result = 31 * result + (int) (teamId ^ (teamId >>> 32));
            return result;
        }
    }
}
