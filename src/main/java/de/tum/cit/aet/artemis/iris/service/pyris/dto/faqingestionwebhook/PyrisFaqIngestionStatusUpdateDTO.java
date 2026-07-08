package de.tum.cit.aet.artemis.iris.service.pyris.dto.faqingestionwebhook;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisRunState;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStatusErrorDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisFaqIngestionStatusUpdateDTO(String result, PyrisRunState runState, @Nullable PyrisStatusErrorDTO error, long jobId) {
}
