package de.tum.cit.aet.artemis.programming.dto;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The policies an instructor changed in the policy editor of the Security Framework, to be previewed or committed to the tests repository.
 *
 * @param baseCommitHash the commit of the tests repository the editor loaded the policies from
 * @param commitMessage  the message of the commit; absent for a default message
 * @param policies       the policies to write, each completely; policies that are not listed stay as they are
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SecurityPolicyChangeDTO(@Nullable String baseCommitHash, @Nullable String commitMessage, List<SecurityPolicyDocumentDTO> policies) {

    /**
     * Replaces omitted policies by an empty list.
     */
    public SecurityPolicyChangeDTO {
        policies = Objects.requireNonNullElse(policies, List.of());
    }
}
