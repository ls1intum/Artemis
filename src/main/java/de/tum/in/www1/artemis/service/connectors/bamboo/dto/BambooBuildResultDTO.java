package de.tum.in.www1.artemis.service.connectors.bamboo.dto;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class BambooBuildResultDTO {

    BambooBuildLogEntriesDTO logEntries;

    public BambooBuildLogEntriesDTO getLogEntries() {
        return logEntries;
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
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class BambooBuildLogEntryDTO {

        @JsonDeserialize(using = UnixTimestampDeserializer.class)
        @JsonSerialize(using = UnixTimestampSerializer.class)
        private ZonedDateTime date;

        private String log;

        private String unstyledLog;

        /**
         * needed for Jackson
         */
        public BambooBuildLogEntryDTO() {
        }

        public BambooBuildLogEntryDTO(ZonedDateTime date, String log) {
            this.date = date;
            this.log = log;
            this.unstyledLog = log;
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
    }

    public static final class UnixTimestampDeserializer extends JsonDeserializer<ZonedDateTime> {

        @Override
        public ZonedDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            String unixTimestamp = parser.getText().trim();
            Instant instant = Instant.ofEpochMilli(Long.parseLong(unixTimestamp));
            return ZonedDateTime.ofInstant(instant, ZoneId.systemDefault());
        }
    }

    public static final class UnixTimestampSerializer extends JsonSerializer<ZonedDateTime> {

        @Override
        public void serialize(ZonedDateTime value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            String dateInMillis = String.valueOf(value.toInstant().toEpochMilli());
            gen.writeString(dateInMillis);
        }
    }
}
