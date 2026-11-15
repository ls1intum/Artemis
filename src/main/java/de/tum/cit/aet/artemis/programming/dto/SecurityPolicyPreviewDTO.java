package de.tum.cit.aet.artemis.programming.dto;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The policy files a change would write, and whether the Ares version the tests repository pins accepts them.
 *
 * @param files            one entry per changed policy
 * @param frameworkVersion the Ares version the tests repository pins
 * @param verdict          {@code COMPATIBLE}, {@code INCOMPATIBLE} or {@code UNVERIFIED}, see
 *                             {@link de.tum.cit.aet.artemis.programming.service.security.AresCompatibilityManifest.Verdict}
 * @param problems         why the pinned version does not accept the files, one entry per file; empty unless {@code INCOMPATIBLE}
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SecurityPolicyPreviewDTO(List<FileDTO> files, @Nullable String frameworkVersion, String verdict, List<String> problems) {

    /**
     * A policy file before and after the change.
     *
     * @param policyPath the path relative to the root of the tests repository
     * @param before     the content as committed; absent for a new policy
     * @param after      the content the change writes
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record FileDTO(String policyPath, @Nullable String before, String after) {
    }
}
