package de.tum.cit.aet.artemis.lecture.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TumLivePlaylistDTO(@JsonProperty("stream") StreamDTO stream) {

    public record StreamDTO(@JsonProperty("playlistUrl") String playlistUrl) {
    }
}
