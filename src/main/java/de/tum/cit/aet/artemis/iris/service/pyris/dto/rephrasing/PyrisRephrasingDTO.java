package de.tum.cit.aet.artemis.iris.service.pyris.dto.rephrasing;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for the Iris rephrasing feature.
 * A rephrasing is just a text and determines the variant of the rephrasing.
 *
 * @param text    The rephrased text
 * @param variant The variant of the rephrasing
 *
 *
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisRephrasingDTO(String text, RephrasingVariant variant) {
}
