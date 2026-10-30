package de.tum.cit.aet.artemis.programming.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The Security Framework activation state of a programming exercise, as sent to the client. Mirrors the client-side {@code SecurityFrameworkConfig}.
 *
 * @param status           {@code ACTIVE} or {@code INACTIVE}, see {@link de.tum.cit.aet.artemis.programming.domain.security.SecurityActivationStatus}
 * @param frameworkVersion the Ares version pinned in the build file of the tests repository; absent if the repository does not pin one
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SecurityFrameworkConfigDTO(String status, String frameworkVersion) {
}
