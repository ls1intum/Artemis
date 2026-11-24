package de.tum.cit.aet.artemis.programming.service.security;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.programming.domain.security.SecurityFrameworkState;
import de.tum.cit.aet.artemis.programming.dto.SecurityFrameworkConfigDTO;

/**
 * Checks the selectable Ares versions against the policy files of a tests repository, so the client can tell which versions can be pinned before the
 * instructor picks one.
 */
final class AresVersionCompatibilities {

    private AresVersionCompatibilities() {
    }

    /**
     * A version is {@code INCOMPATIBLE} as soon as one file uses something it does not accept, and {@code UNVERIFIED} if it is not incompatible but one file
     * could not be checked.
     *
     * @param manifest      which policy fields each Ares release accepts
     * @param versions      the selectable versions, newest first
     * @param state         the state whose policy files are checked
     * @param contentByPath reads a policy file by its validated path; null if it cannot be read
     * @return one entry per version, in the given order; empty if none of the policy files of the state can be read
     */
    static List<SecurityFrameworkConfigDTO.VersionCompatibilityDTO> check(AresCompatibilityManifest manifest, List<String> versions, SecurityFrameworkState state,
            UnaryOperator<@Nullable String> contentByPath) {
        Map<String, String> contents = new LinkedHashMap<>();
        // A file that cannot be read cannot be checked, so a version that accepts all other files is not verified.
        boolean someFileUnchecked = false;
        for (SecurityFrameworkState.PolicyFile policyFile : state.policyFiles()) {
            String path = SecurityPolicyRepositoryFiles.validatePolicyPath(policyFile.policyPath()).orElse(null);
            String content = path == null ? null : contentByPath.apply(path);
            if (path != null && content != null) {
                contents.put(path, content);
            }
            else {
                someFileUnchecked = true;
            }
        }
        if (contents.isEmpty()) {
            return List.of();
        }
        List<SecurityFrameworkConfigDTO.VersionCompatibilityDTO> compatibilities = new ArrayList<>();
        for (String version : versions) {
            List<String> incompatibilities = new ArrayList<>();
            boolean unverified = someFileUnchecked;
            for (Map.Entry<String, String> file : contents.entrySet()) {
                AresCompatibilityManifest.Check check = manifest.check(version, file.getValue());
                unverified |= check.verdict() == AresCompatibilityManifest.Verdict.UNVERIFIED;
                SecurityFrameworkService.describeIncompatibility(file.getKey(), check).ifPresent(incompatibilities::add);
            }
            AresCompatibilityManifest.Verdict verdict;
            if (!incompatibilities.isEmpty()) {
                verdict = AresCompatibilityManifest.Verdict.INCOMPATIBLE;
            }
            else if (unverified) {
                verdict = AresCompatibilityManifest.Verdict.UNVERIFIED;
            }
            else {
                verdict = AresCompatibilityManifest.Verdict.COMPATIBLE;
            }
            compatibilities.add(new SecurityFrameworkConfigDTO.VersionCompatibilityDTO(version, verdict.name(), incompatibilities));
        }
        return compatibilities;
    }
}
