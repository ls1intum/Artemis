package de.tum.cit.aet.artemis.sharing;

import org.springframework.context.annotation.Profile;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.dto.SharingInfoDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * the sharing info, wrapping the original sharing Info from the sharing platform and adding course and exercise info.
 */
@Profile("sharing")
public class SharingSetupInfo {

    /**
     * the exercise
     */
    ProgrammingExercise exercise;

    /**
     * the course
     */
    Course course;

    /**
     * the sharing info from the sharing platform
     */
    SharingInfoDTO sharingInfo;

    /**
     * the exercise
     */
    public ProgrammingExercise getExercise() {
        return exercise;
    }

    /**
     * sets the exercise
     */
    public void setExercise(ProgrammingExercise exercise) {
        this.exercise = exercise;
    }

    /**
     * the course
     */
    public Course getCourse() {
        return course;
    }

    /**
     * sets the course
     */
    public void setCourse(Course course) {
        this.course = course;
    }

    /**
     * the sharing info from the sharing platform
     */
    public SharingInfoDTO getSharingInfo() {
        return sharingInfo;
    }

    /**
     * sets the sharing info from the sharing platform
     */
    public void setSharingInfo(SharingInfoDTO sharingInfo) {
        this.sharingInfo = sharingInfo;
    }
}
