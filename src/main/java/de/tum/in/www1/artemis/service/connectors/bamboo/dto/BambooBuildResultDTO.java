package de.tum.in.www1.artemis.service.connectors.bamboo.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BambooBuildResultDTO {

    BambooBuildLogEntriesDTO logEntries;

    public BambooBuildLogEntriesDTO getLogEntries() {
        return logEntries;
    }

    public void setLogEntries(BambooBuildLogEntriesDTO logEntries) {
        this.logEntries = logEntries;
    }

    public BambooBuildResultDTO() {
    }

    public BambooBuildResultDTO(BambooBuildLogEntriesDTO logEntries) {
        this.logEntries = logEntries;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class BambooBuildLogEntriesDTO {

        List<BambooBuildLogDTO> logEntry = new ArrayList<>();

        public BambooBuildLogEntriesDTO() {
        }

        public BambooBuildLogEntriesDTO(List<BambooBuildLogDTO> logEntry) {
            this.logEntry = logEntry;
        }

        public List<BambooBuildLogDTO> getLogEntry() {
            return logEntry;
        }

        public void setLogEntry(List<BambooBuildLogDTO> logEntry) {
            this.logEntry = logEntry;
        }
    }
}
