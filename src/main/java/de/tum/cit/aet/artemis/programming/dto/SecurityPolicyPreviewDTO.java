package de.tum.cit.aet.artemis.programming.dto;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The files a change would write, and whether the Ares version the tests repository pins accepts its policies.
 *
 * @param files            one entry per changed file: the policies, the build file if the change pins another Ares version, and the test classes whose
 *                             {@code @Policy} annotations it rewrites
 * @param frameworkVersion the Ares version the tests repository pins after the change
 * @param verdict          {@code COMPATIBLE}, {@code INCOMPATIBLE} or {@code UNVERIFIED}, see
 *                             {@link de.tum.cit.aet.artemis.programming.service.security.AresCompatibilityManifest.Verdict}
 * @param problems         why the pinned version does not accept the files, one entry per file; empty unless {@code INCOMPATIBLE}
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SecurityPolicyPreviewDTO(List<FileDTO> files, @Nullable String frameworkVersion, String verdict, List<String> problems) {

    /**
     * A file before and after the change.
     *
     * @param path         the path relative to the root of the tests repository
     * @param previousPath the path the file had before; differs from {@code path} for a renamed policy and is absent for a new one
     * @param before       the content as committed; absent for a new policy
     * @param after        the content the change writes; absent for a deleted policy
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record FileDTO(String path, @Nullable String previousPath, @Nullable String before, @Nullable String after) {
    }
}
