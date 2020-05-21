package de.tum.in.www1.artemis.web.rest.dto;

public class DueDateStat {

    private Long inTime;

    private Long late;

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
