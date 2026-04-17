package de.tum.cit.aet.artemis.deimos.service;

public record DeimosLlmRequest(long participationId, String systemPrompt, String userPrompt) {
}
