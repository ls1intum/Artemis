package de.tum.cit.aet.artemis.sharing;

import org.springframework.context.annotation.Profile;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.dto.SharingInfoDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

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
