package de.tum.cit.aet.artemis.deimos.service;

public interface DeimosLlmClient {

    DeimosLlmResponse analyze(DeimosLlmRequest request);
}
