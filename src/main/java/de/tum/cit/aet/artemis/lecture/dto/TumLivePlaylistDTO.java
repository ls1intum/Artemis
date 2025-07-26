package de.tum.cit.aet.artemis.lecture.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TumLivePlaylistDTO(@JsonProperty("stream") Stream stream) {

    public record Stream(@JsonProperty("playlistUrl") String playlistUrl) {
    }
}
