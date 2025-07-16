package de.tum.cit.aet.artemis.lecture.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class TumLivePlaylistDTO {

    private final String playlistUrl;

    @JsonCreator
    public TumLivePlaylistDTO(@JsonProperty("stream") Map<String, Object> stream) {
        this.playlistUrl = stream != null && stream.get("playlistUrl") instanceof String url ? url : null;
    }

    public String getPlaylistUrl() {
        return playlistUrl;
    }
}
