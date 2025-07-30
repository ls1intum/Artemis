package de.tum.cit.aet.artemis.nebula.dto;

import java.util.List;

import de.tum.cit.aet.artemis.communication.dto.FaqDTO;

public record FaqConsistencyDTO(String toBeChecked, List<FaqDTO> faqs) {
}
