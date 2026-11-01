package de.tum.cit.aet.artemis.programming.dto;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.security.SecurityFrameworkState;

/**
 * The Security Framework state of a programming exercise, as sent to the client. Mirrors the client-side {@code SecurityFrameworkConfig}.
 *
 * @param status              {@code ACTIVE}, {@code INACTIVE}, {@code MISCONFIGURED} or {@code UNSUPPORTED}, see
 *                                {@link de.tum.cit.aet.artemis.programming.domain.security.SecurityActivationStatus}
 * @param frameworkVersion    the Ares version pinned in the build file of the tests repository; absent if the repository does not pin one
 * @param disabledTestClasses the test classes whose policy is switched off
 * @param policyFiles         the policy files that active policies point to, with their problems and the test classes they do not list
 * @param unsupportedReasons  why the repository cannot be managed automatically; only for {@code UNSUPPORTED}
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SecurityFrameworkConfigDTO(String status, @Nullable String frameworkVersion, List<String> disabledTestClasses, List<PolicyFileDTO> policyFiles,
        List<UnsupportedReasonDTO> unsupportedReasons) {

    /**
     * A policy file that an active policy points to.
     *
     * @param policyPath         the path relative to the repository root
     * @param referencedBy       the test classes that point to it
     * @param problem            why Ares cannot use it or why it cannot be checked; absent if it is fine
     * @param missingTestClasses the test classes that point to it but are not listed in it
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record PolicyFileDTO(String policyPath, List<String> referencedBy, @Nullable String problem, List<String> missingTestClasses) {
    }

    /**
     * A reason why the repository cannot be managed automatically.
     *
     * @param location the affected file, class or build file
     * @param type     the reason, see {@link SecurityFrameworkState.UnsupportedReasonType}
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record UnsupportedReasonDTO(String location, String type) {
    }

    /**
     * @param state            the state of the tests repository
     * @param frameworkVersion the Ares version pinned in its build file, if any
     * @return the DTO for the state
     */
    public static SecurityFrameworkConfigDTO of(SecurityFrameworkState state, @Nullable String frameworkVersion) {
        List<PolicyFileDTO> policyFiles = state.policyFiles().stream().map(policyFile -> new PolicyFileDTO(policyFile.policyPath(), policyFile.referencedBy(),
                policyFile.problem() == null ? null : policyFile.problem().name(), policyFile.missingTestClasses())).toList();
        List<UnsupportedReasonDTO> reasons = state.unsupportedReasons().stream().map(reason -> new UnsupportedReasonDTO(reason.location(), reason.type().name())).toList();
        return new SecurityFrameworkConfigDTO(state.status().name(), frameworkVersion, state.disabledTestClasses(), policyFiles, reasons);
    }
}
