package de.tum.cit.aet.artemis.nebula.dto;

import jakarta.validation.constraints.NotNull;

public record FaqRewritingResponse(@NotNull String rewrittenText) {
}
