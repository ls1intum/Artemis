package de.tum.cit.aet.artemis.programming.dto;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The changes an instructor made in the policy editor of the Security Framework, to be previewed or committed to the tests repository in one commit.
 *
 * @param baseCommitHash     the commit of the tests repository the editor loaded the policies from
 * @param commitMessage      the message of the commit; absent for a default message
 * @param frameworkVersion   the Ares version to pin with the commit; absent to keep the pinned one
 * @param policies           the policies to write or reassign, each completely; policies that are not listed stay as they are
 * @param removedPolicyPaths the policy files to delete; their tests use the strict default policy of Ares afterwards unless another policy is assigned
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SecurityPolicyChangeDTO(@Nullable String baseCommitHash, @Nullable String commitMessage, @Nullable String frameworkVersion, List<PolicyChangeDTO> policies,
        List<String> removedPolicyPaths) {

    /**
     * Replaces omitted lists by empty ones.
     */
    public SecurityPolicyChangeDTO {
        policies = Objects.requireNonNullElse(policies, List.of());
        removedPolicyPaths = Objects.requireNonNullElse(removedPolicyPaths, List.of());
    }

    /**
     * One policy file to write.
     *
     * @param previousPolicyPath the path the file has in the repository; absent for a new policy, and another path than the document's when it is renamed
     * @param document           the content of the policy
     * @param assignedTests      the tests that use the policy: a test class for all of its test cases, or {@code Class#method} for a single test case
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record PolicyChangeDTO(@Nullable String previousPolicyPath, SecurityPolicyDocumentDTO document, List<String> assignedTests) {

        /**
         * Replaces omitted tests by an empty list.
         */
        public PolicyChangeDTO {
            assignedTests = Objects.requireNonNullElse(assignedTests, List.of());
        }
    }
}
