package de.tum.cit.aet.artemis.buildagent.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BuildLogDTO(@NotNull ZonedDateTime time, @NotNull String log) implements Serializable {

}
