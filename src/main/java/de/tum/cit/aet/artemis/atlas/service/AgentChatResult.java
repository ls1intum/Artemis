package de.tum.cit.aet.artemis.atlas.service;

/**
 * Internal result object for Atlas Agent chat processing.
 * Contains the response message and whether competencies were modified.
 */
public record AgentChatResult(String message, boolean competenciesModified) {
}
