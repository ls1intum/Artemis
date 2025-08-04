package de.tum.cit.aet.artemis.lecture.dto;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TumLivePlaylistDTO(@NotNull @JsonProperty("stream") StreamDTO stream) {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record StreamDTO(@NotNull @JsonProperty("playlistUrl") String playlistUrl) {
    }
}
