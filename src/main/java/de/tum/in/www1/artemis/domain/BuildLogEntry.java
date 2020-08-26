package de.tum.in.www1.artemis.domain;

import java.time.ZonedDateTime;
import java.util.Objects;

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

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        BuildLogEntry that = (BuildLogEntry) object;
        if (time != null && that.time != null) {
            return Objects.equals(time.toInstant(), that.time.toInstant()) && Objects.equals(log, that.log);
        }
        else if (time == null && that.time == null) {
            return Objects.equals(log, that.log);
        }
        else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(time, log);
    }
}
