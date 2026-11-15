package de.tum.cit.aet.artemis.programming.dto;

import java.time.ZonedDateTime;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The security policies of a programming exercise as the policy editor of the Security Framework loads them from the tests repository (Objective 2).
 *
 * @param baseCommitHash   the commit of the tests repository the policies were read from; a change is only committed if the policies did not change since
 * @param lastCommit       the most recent commit of the tests repository; absent if it has none
 * @param frameworkVersion the Ares version the tests repository pins
 * @param policies         the policy files that active {@code @Policy} annotations point to, sorted by path
 * @param presets          the presets an instructor can base a policy on
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SecurityPolicyEditorDTO(@Nullable String baseCommitHash, @Nullable CommitDTO lastCommit, @Nullable String frameworkVersion, List<PolicyDTO> policies,
        List<PresetDTO> presets) {

    /**
     * A commit of the tests repository.
     *
     * @param hash       the full commit hash
     * @param authorName the name of its author
     * @param date       when it was committed
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record CommitDTO(String hash, @Nullable String authorName, ZonedDateTime date) {
    }

    /**
     * One policy file.
     *
     * @param policyPath     the path relative to the root of the tests repository
     * @param content        the content of the file as committed; absent if it cannot be read
     * @param document       the content as the editor edits it; absent if the policy is shown read-only
     * @param readOnlyReason why the editor cannot change the file without losing something, see
     *                           {@link de.tum.cit.aet.artemis.programming.service.security.SecurityPolicyDocuments.ReadOnlyReason}
     * @param errorLine      the line of the YAML error, 1-based, for {@code YAML_ERROR}
     * @param referencedBy   the test classes whose active {@code @Policy} annotation points to the file
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record PolicyDTO(String policyPath, @Nullable String content, @Nullable SecurityPolicyDocumentDTO document, @Nullable String readOnlyReason, @Nullable Integer errorLine,
            List<String> referencedBy) {
    }

    /**
     * A preset of Ares that a policy can be based on. The permissions it adds are shown as locked rows.
     *
     * @param name     the name Ares reads in {@code basedOnTheFollowingPreset}
     * @param label    the name shown to instructors
     * @param files    the file system permissions it adds
     * @param network  the network permissions it adds
     * @param commands the command permissions it adds
     * @param threads  the thread permissions it adds
     * @param packages the package imports it allows
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record PresetDTO(String name, String label, List<SecurityPolicyDocumentDTO.FilePermissionDTO> files, List<SecurityPolicyDocumentDTO.NetworkPermissionDTO> network,
            List<SecurityPolicyDocumentDTO.CommandPermissionDTO> commands, List<SecurityPolicyDocumentDTO.ThreadPermissionDTO> threads,
            List<SecurityPolicyDocumentDTO.PackagePermissionDTO> packages) {
    }
}
