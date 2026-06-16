package de.tum.cit.aet.artemis.deimos.service;

import de.tum.cit.aet.artemis.deimos.dto.DeimosLlmRequest;
import de.tum.cit.aet.artemis.deimos.dto.DeimosLlmResponse;

public interface DeimosLlmClient {

    DeimosLlmResponse analyze(DeimosLlmRequest request);
}
