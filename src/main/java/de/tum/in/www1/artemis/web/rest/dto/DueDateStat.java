package de.tum.in.www1.artemis.web.rest.dto;

/**
 * Wrapper class for a two-component statistic
 * depending on the due-date of an exercise.
 */
public class DueDateStat {

    // The statistic component before the due-date
    private long inTime;

    // The statistic component after the due-date
    private long late;

    public DueDateStat() {
        // default constructor for our beloved Jackson serializer :-*
    }

    public DueDateStat(long inTime, long late) {
        this.inTime = inTime;
        this.late = late;
    }

    public long getInTime() {
        return inTime;
    }

    public void setInTime(long inTime) {
        this.inTime = inTime;
    }

    public long getLate() {
        return late;
    }

    public void setLate(long late) {
        this.late = late;
    }
}
