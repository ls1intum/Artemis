package de.tum.cit.aet.artemis.iris.service.pyris.dto.data;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.rewriting.RewritingVariant;

public record PyrisRewriteTextRequest(String toBeRewritten, RewritingVariant variant) {
}
