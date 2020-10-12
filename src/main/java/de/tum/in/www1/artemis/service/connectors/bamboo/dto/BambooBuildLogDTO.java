package de.tum.in.www1.artemis.service.connectors.bamboo.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BambooBuildLogDTO {

    private ZonedDateTime date;

    private String log;

    private String unstyledLog;

    public BambooBuildLogDTO() {
    }

    public BambooBuildLogDTO(ZonedDateTime date, String log, String unstyledLog) {
        this.date = date;
        this.log = log;
        this.unstyledLog = unstyledLog;
    }

    public ZonedDateTime getDate() {
        return date;
    }

    public void setDate(ZonedDateTime date) {
        this.date = date;
    }

    public String getLog() {
        return log;
    }

    public void setLog(String log) {
        this.log = log;
    }

    public String getUnstyledLog() {
        return unstyledLog;
    }

    public void setUnstyledLog(String unstyledLog) {
        this.unstyledLog = unstyledLog;
    }
}
