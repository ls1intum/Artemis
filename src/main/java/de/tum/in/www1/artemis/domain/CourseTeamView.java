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

    /**
     * Empty constructor needed for jackson
     */
    public CourseTeamView() {
        // needed for jackson
    }

    public CourseTeamViewId getCourseTeamViewId() {
        return courseTeamViewId;
    }

    public void setCourseTeamViewId(CourseTeamViewId courseTeamViewId) {
        this.courseTeamViewId = courseTeamViewId;
    }

    @Immutable
    @Embeddable
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class CourseTeamViewId implements Serializable {

        @Column(name = "COURSE_ID")
        private long courseId;

        @Column(name = "TEAM_ID")
        private long teamId;

        public CourseTeamViewId(long courseId, long teamId) {
            this.courseId = courseId;
            this.teamId = teamId;
        }

        /**
         * Empty constructor needed for jackson
         */
        public CourseTeamViewId() {
            // needed for jackson
        }

        public long getCourseId() {
            return courseId;
        }

        public void setCourseId(long courseId) {
            this.courseId = courseId;
        }

        public long getTeamId() {
            return teamId;
        }

        public void setTeamId(long teamId) {
            this.teamId = teamId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            CourseTeamViewId that = (CourseTeamViewId) o;

            if (courseId != that.courseId) {
                return false;
            }
            return teamId == that.teamId;
        }

        @Override
        public int hashCode() {
            int result = (int) (courseId ^ (courseId >>> 32));
            result = 31 * result + (int) (teamId ^ (teamId >>> 32));
            return result;
        }
    }
}
