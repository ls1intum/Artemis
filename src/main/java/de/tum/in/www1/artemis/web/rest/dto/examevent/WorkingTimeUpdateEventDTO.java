package de.tum.in.www1.artemis.web.rest.dto.examevent;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A DTO for the {@link de.tum.in.www1.artemis.domain.exam.event.WorkingTimeUpdateEvent} entity.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        WorkingTimeUpdateEventDTO that = (WorkingTimeUpdateEventDTO) o;
        return newWorkingTime == that.newWorkingTime && oldWorkingTime == that.oldWorkingTime && courseWide == that.courseWide;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), newWorkingTime, oldWorkingTime, courseWide);
    }
}
