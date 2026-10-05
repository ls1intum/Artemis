package de.tum.cit.aet.artemis.deimos.dto;

public record DeimosLlmRequest(long participationId, String systemPrompt, String userPrompt) {
}
