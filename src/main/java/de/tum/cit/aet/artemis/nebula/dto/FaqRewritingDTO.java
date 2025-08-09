package de.tum.cit.aet.artemis.nebula.dto;

import java.util.List;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.dto.FaqDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FaqRewritingDTO(@NotNull String toBeRewritten, @Nullable List<FaqDTO> faqs) {
}
