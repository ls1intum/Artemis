package de.tum.cit.aet.artemis.programming.service.hades.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record HadesLogEntryDTO(@JsonProperty("timestamp") ZonedDateTime timestamp, @JsonProperty("message") String message, @JsonProperty("output_stream") String outputStream) {
}
