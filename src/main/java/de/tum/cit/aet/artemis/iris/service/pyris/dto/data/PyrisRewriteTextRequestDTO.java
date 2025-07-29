package de.tum.cit.aet.artemis.iris.service.pyris.dto.data;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.rewriting.RewritingVariant;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
/**
 * DTO for rewriting text using Pyris.
 *
 * @param toBeRewritten The text that needs to be rewritten
 * @param variant       The variant of rewriting to be applied
 */
public record PyrisRewriteTextRequestDTO(String toBeRewritten, RewritingVariant variant) {
}
