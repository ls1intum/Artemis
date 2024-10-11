package de.tum.in.www1.artemis.domain.sharing;

import org.springframework.context.annotation.Profile;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.web.rest.dto.SharingInfoDTO;

@Profile("sharing")
public class SharingSetupInfo {

    ProgrammingExercise exercise;

    Course course;

    SharingInfoDTO sharingInfo;

    public ProgrammingExercise getExercise() {
        return exercise;
    }

    public void setExercise(ProgrammingExercise exercise) {
        this.exercise = exercise;
    }

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public SharingInfoDTO getSharingInfo() {
        return sharingInfo;
    }

    public void setSharingInfo(SharingInfoDTO sharingInfo) {
        this.sharingInfo = sharingInfo;
    }
}
