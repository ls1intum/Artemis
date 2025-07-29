package de.tum.cit.aet.artemis.nebula.dto;

import java.util.List;

import de.tum.cit.aet.artemis.communication.dto.FaqDTO;

public record FaqRewritingDTO(long userId, long courseId, String toBeRewritten, List<FaqDTO> faqs) {
}
