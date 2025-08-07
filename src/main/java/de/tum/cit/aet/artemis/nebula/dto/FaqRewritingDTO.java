package de.tum.cit.aet.artemis.nebula.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.dto.FaqDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FaqRewritingDTO(String toBeRewritten, List<FaqDTO> faqs) {
}
