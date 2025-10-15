package de.tum.cit.aet.artemis.atlas.service;

import javax.validation.constraints.NotNull;

/**
 * Internal result object for Atlas Agent chat processing.
 * Contains the response message and whether competencies were modified.
 */
public record AgentChatResult(@NotNull String message, boolean competenciesModified) {
}
