package de.tum.cit.aet.artemis.atlas.service;

import org.jspecify.annotations.NonNull;

/**
 * Internal result object for Atlas Agent chat processing.
 * Contains the response message and whether competencies were modified.
 */
public record AgentChatResult(@NonNull String message, boolean competenciesModified) {
}
