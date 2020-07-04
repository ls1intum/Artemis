package de.tum.in.www1.artemis.web.rest.dto;

/**
 * Wrapper class for a two-component statistic
 * depending on the due-date of an exercise.
 */
public class DueDateStat {

    // The statistic component before the due-date
    private Long inTime;

    // The statistic component after the due-date
    private Long late;

    public DueDateStat() {
        // default constructor for our beloved Jackson serializer :-*
    }

    public DueDateStat(Long inTime, Long late) {
        this.inTime = inTime;
        this.late = late;
    }

    public Long getInTime() {
        return inTime;
    }

    public void setInTime(Long inTime) {
        this.inTime = inTime;
    }

    public Long getLate() {
        return late;
    }

    public void setLate(Long late) {
        this.late = late;
    }
}
