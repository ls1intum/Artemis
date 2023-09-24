package de.tum.in.www1.artemis.web.rest.dto.examevent;

/**
 * A DTO for the {@link de.tum.in.www1.artemis.domain.exam.event.WorkingTimeUpdateEvent} entity.
 */
public class WorkingTimeUpdateEventDTO extends ExamLiveEventDTO {

    private int newWorkingTime;

    private int oldWorkingTime;

    private boolean courseWide;

    public int getNewWorkingTime() {
        return newWorkingTime;
    }

    public void setNewWorkingTime(int newWorkingTime) {
        this.newWorkingTime = newWorkingTime;
    }

    public int getOldWorkingTime() {
        return oldWorkingTime;
    }

    public void setOldWorkingTime(int oldWorkingTime) {
        this.oldWorkingTime = oldWorkingTime;
    }

    public boolean isCourseWide() {
        return courseWide;
    }

    public void setCourseWide(boolean courseWide) {
        this.courseWide = courseWide;
    }
}
