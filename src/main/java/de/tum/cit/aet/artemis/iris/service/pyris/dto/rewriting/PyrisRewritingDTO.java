package de.tum.cit.aet.artemis.iris.service.pyris.dto.rewriting;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for the Iris rewriting feature.
 * A rewriting is just a text and determines the variant of the rewriting.
 *
 * @param text    The text that should be rewritten
 * @param variant The variant of the rewriting
 *
 *
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisRewritingDTO(String text, RewritingVariant variant) {
}
