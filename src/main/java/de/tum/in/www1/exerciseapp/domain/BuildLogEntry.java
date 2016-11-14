package de.tum.in.www1.exerciseapp.domain;

import java.time.ZonedDateTime;

/**
 * Created by Josias Montag on 11.11.16.
 */
public class BuildLogEntry {

    private ZonedDateTime time;
    private String log;

    public BuildLogEntry(ZonedDateTime time, String log) {
        this.time = time;
        this.log = log;
    }

    public ZonedDateTime getTime() {
        return time;
    }

    public void setTime(ZonedDateTime time) {
        this.time = time;
    }

    public String getLog() {
        return log;
    }

    public void setLog(String log) {
        this.log = log;
    }

}
