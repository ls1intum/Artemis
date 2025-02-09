package de.tum.cit.aet.artemis.iris.service.pyris.dto.data;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.rewriting.RewritingVariant;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisRewriteTextRequestDTO(String toBeRewritten, RewritingVariant variant) {
}
