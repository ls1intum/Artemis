package de.tum.cit.aet.artemis.buildagent.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.build.BuildLogEntry;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BuildLogDTO(@NotNull ZonedDateTime time, @NotNull String log) implements Serializable {

    public BuildLogEntry toBuildLogEntry() {
        return new BuildLogEntry(this.time, this.log);
    }
}
