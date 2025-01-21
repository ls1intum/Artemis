package de.tum.cit.aet.artemis.iris.service.pyris.dto.data;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.rewriting.RewritingVariant;

public record PyrisRewriteTextRequestDTO(String toBeRewritten, RewritingVariant variant) {
}
