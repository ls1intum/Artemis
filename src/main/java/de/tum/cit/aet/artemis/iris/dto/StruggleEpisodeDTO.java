package de.tum.cit.aet.artemis.iris.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Client-allocated slot episode, carried on both the inbound extension→Artemis request and the outbound
 * Artemis→Pyris execution payload. Uses bare {@code @JsonInclude()} (i.e. Include.ALWAYS) deliberately --
 * the global architecture rule forbids any explicit value other than NON_EMPTY, and the contract requires
 * a first FREE-slot {@code decide} to serialize {@code "hints":[]} with the empty list PRESENT (NON_EMPTY
 * would drop it, causing a cross-repo break).
 */
@JsonInclude
public record StruggleEpisodeDTO(String episodeId, boolean isNew, List<StruggleEpisodeHintDTO> hints) {

    public StruggleEpisodeDTO {
        hints = hints != null ? hints : List.of();
    }
}
