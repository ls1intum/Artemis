package de.tum.in.www1.artemis.web.rest.dto;

public class DueDateStat<T extends Number> {

    private T inTime;

    private T late;

    public DueDateStat(T inTime, T late) {
        this.inTime = inTime;
        this.late = late;
    }

    public T getInTime() {
        return inTime;
    }

    public void setInTime(T inTime) {
        this.inTime = inTime;
    }

    public T getLate() {
        return late;
    }

    public void setLate(T late) {
        this.late = late;
    }
}
