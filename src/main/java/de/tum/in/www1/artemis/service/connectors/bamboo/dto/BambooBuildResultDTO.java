package de.tum.in.www1.artemis.service.connectors.bamboo.dto;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

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

        List<BambooBuildLogEntryDTO> logEntry = new ArrayList<>();

        public BambooBuildLogEntriesDTO() {
        }

        public BambooBuildLogEntriesDTO(List<BambooBuildLogEntryDTO> logEntry) {
            this.logEntry = logEntry;
        }

        public List<BambooBuildLogEntryDTO> getLogEntry() {
            return logEntry;
        }

        public void setLogEntry(List<BambooBuildLogEntryDTO> logEntry) {
            this.logEntry = logEntry;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class BambooBuildLogEntryDTO {

        @JsonDeserialize(using = UnixTimestampDeserializer.class)
        private ZonedDateTime date;

        private String log;

        private String unstyledLog;

        public BambooBuildLogEntryDTO() {
        }

        public BambooBuildLogEntryDTO(ZonedDateTime date, String log, String unstyledLog) {
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

    public static final class UnixTimestampDeserializer extends JsonDeserializer<ZonedDateTime> {

        @Override
        public ZonedDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            String unixTimestamp = parser.getText().trim();
            Instant instant = Instant.ofEpochMilli(Long.parseLong(unixTimestamp));
            return ZonedDateTime.ofInstant(instant, ZoneId.systemDefault());
        }
    }

}
