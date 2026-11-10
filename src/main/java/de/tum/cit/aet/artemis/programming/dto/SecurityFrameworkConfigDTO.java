package de.tum.cit.aet.artemis.programming.dto;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.security.SecurityFrameworkState;

/**
 * The Security Framework state of a programming exercise, as sent to the client. Mirrors the client-side {@code SecurityFrameworkConfig}.
 *
 * @param status                 {@code CONFIGURED}, {@code INACTIVE}, {@code MISCONFIGURED} or {@code UNSUPPORTED}, see
 *                                   {@link de.tum.cit.aet.artemis.programming.domain.security.SecurityActivationStatus}
 * @param frameworkVersion       the Ares version pinned in the build file of the tests repository; absent if the repository does not pin one
 * @param disabledTestClasses    the test classes whose policy is switched off
 * @param policyFiles            the policy files that active policies point to, with their problems and the test classes they do not list
 * @param unsupportedReasons     why the repository cannot be managed automatically; only for {@code UNSUPPORTED}
 * @param versionCompatibilities whether each selectable Ares version accepts the policy files, see decision B10 of Ares2 issue #386; empty if no policy
 *                                   file is read, e.g. while the sandbox is inactive
 * @param aresApiUses            the parts of Ares the tests use besides the sandbox, so the Ares library cannot be removed from the build without migrating
 *                                   them first (decision B4 of Ares2 issue #386)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SecurityFrameworkConfigDTO(String status, @Nullable String frameworkVersion, List<String> disabledTestClasses, List<PolicyFileDTO> policyFiles,
        List<UnsupportedReasonDTO> unsupportedReasons, List<VersionCompatibilityDTO> versionCompatibilities, List<AresApiUseDTO> aresApiUses) {

    /**
     * A part of Ares a test file uses besides the sandbox.
     *
     * @param location the file, relative to the repository root
     * @param api      the type or wildcard-imported package, relative to the Ares package, e.g. {@code StrictTimeout} or {@code structural.*}
     */
    public record AresApiUseDTO(String location, String api) {
    }

    /**
     * Whether an Ares version accepts the policy files of the exercise.
     *
     * @param version           the Ares version
     * @param verdict           {@code COMPATIBLE}, {@code INCOMPATIBLE} or {@code UNVERIFIED}, see
     *                              {@link de.tum.cit.aet.artemis.programming.service.security.AresCompatibilityManifest.Verdict}
     * @param incompatibilities per policy file, what the version does not accept, e.g. {@code SecurityPolicy.yaml: a.b, a.c}; only for {@code INCOMPATIBLE}
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record VersionCompatibilityDTO(String version, String verdict, List<String> incompatibilities) {
    }

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
     * @param state                  the state of the tests repository
     * @param frameworkVersion       the Ares version pinned in its build file, if any
     * @param versionCompatibilities whether each selectable Ares version accepts its policy files
     * @return the DTO for the state
     */
    public static SecurityFrameworkConfigDTO of(SecurityFrameworkState state, @Nullable String frameworkVersion, List<VersionCompatibilityDTO> versionCompatibilities) {
        List<PolicyFileDTO> policyFiles = state.policyFiles().stream().map(policyFile -> new PolicyFileDTO(policyFile.policyPath(), policyFile.referencedBy(),
                policyFile.problem() == null ? null : policyFile.problem().name(), policyFile.missingTestClasses())).toList();
        List<UnsupportedReasonDTO> reasons = state.unsupportedReasons().stream().map(reason -> new UnsupportedReasonDTO(reason.location(), reason.type().name())).toList();
        List<AresApiUseDTO> aresApiUses = state.aresApiUses().stream().map(use -> new AresApiUseDTO(use.location(), use.api())).toList();
        return new SecurityFrameworkConfigDTO(state.status().name(), frameworkVersion, state.disabledTestClasses(), policyFiles, reasons, versionCompatibilities, aresApiUses);
    }
}
