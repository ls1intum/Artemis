package de.tum.cit.aet.artemis.communication.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Every FAQ row the Iris index holds for this Artemis instance, plus legacy rows without an instance, as reported by the Pyris FAQ census.
 *
 * @param faqs one entry per course and FAQ
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FaqCensusDTO(List<FaqCensusEntryDTO> faqs) {

    public FaqCensusDTO {
        faqs = faqs == null ? List.of() : List.copyOf(faqs);
    }
}
