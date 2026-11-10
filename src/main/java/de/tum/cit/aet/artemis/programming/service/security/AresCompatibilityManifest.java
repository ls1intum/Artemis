package de.tum.cit.aet.artemis.programming.service.security;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * Which policy-format versions and policy fields each Ares release accepts (decision B10 of Ares2 issue #386).
 * <p>
 * Ares fails every test if the policy contains a field the pinned release does not know (decision A3). Before Artemis pins a release, it therefore checks
 * every policy file of the exercise against the entry of that release. The Ares version and the policy-format version
 * ({@code thisPolicyFileCompliesToThePolicyVersion}) are different numbers, so both are checked.
 * <p>
 * The manifest only knows which fields exist, not which values they accept; the values are checked by {@link SecurityPolicySchema} for the release it was
 * verified against. A release that the manifest does not list cannot be checked and is reported as {@link Verdict#UNVERIFIED}.
 * <p>
 * The file format is documented in {@code security-framework/ares-compatibility.yml}.
 */
public final class AresCompatibilityManifest {

    /** The version of the manifest format this class reads. */
    static final int MANIFEST_VERSION = 1;

    /** Stands for every entry of a list of mappings in a field path. */
    static final String LIST_ENTRY = "[]";

    private static final String FORMAT_VERSION_KEY = "thisPolicyFileCompliesToThePolicyVersion";

    private static final AresCompatibilityManifest EMPTY = new AresCompatibilityManifest(Map.of());

    private final Map<String, Release> releases;

    /**
     * What one Ares release accepts.
     *
     * @param aresVersion          the release, as it is pinned in the build file
     * @param minimumFormatVersion the oldest policy-format version it accepts
     * @param maximumFormatVersion the newest policy-format version it accepts
     * @param fields               the field paths it accepts
     */
    public record Release(String aresVersion, int minimumFormatVersion, int maximumFormatVersion, Set<String> fields) {
    }

    /** The result of checking a policy against a release. */
    public enum Verdict {
        /** The release accepts every field and the format version of the policy. */
        COMPATIBLE,
        /** The release does not accept a field or the format version of the policy, so Ares would fail every test. */
        INCOMPATIBLE,
        /** The manifest does not list the release, or the policy cannot be read, so compatibility could not be checked. */
        UNVERIFIED
    }

    /**
     * @param verdict                  the result
     * @param unsupportedFields        the field paths the release does not accept, in the order of the file, each once; only the outermost unknown field of a subtree is listed
     * @param unsupportedFormatVersion the format version of the policy if the release does not accept it
     */
    public record Check(Verdict verdict, List<String> unsupportedFields, @Nullable BigInteger unsupportedFormatVersion) {

        static final Check COMPATIBLE = new Check(Verdict.COMPATIBLE, List.of(), null);

        static final Check UNVERIFIED = new Check(Verdict.UNVERIFIED, List.of(), null);
    }

    private AresCompatibilityManifest(Map<String, Release> releases) {
        this.releases = releases;
    }

    /**
     * @return a manifest that lists no release, so every release is {@link Verdict#UNVERIFIED}
     */
    public static AresCompatibilityManifest empty() {
        return EMPTY;
    }

    /**
     * Reads a manifest.
     *
     * @param yaml the content of the manifest file
     * @return the manifest
     * @throws IllegalArgumentException if the content is not a valid manifest
     */
    public static AresCompatibilityManifest parse(String yaml) {
        Object document;
        try {
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            document = new Yaml(new SafeConstructor(options)).load(yaml);
        }
        catch (YAMLException e) {
            throw new IllegalArgumentException("The compatibility manifest is not valid YAML", e);
        }
        if (!(document instanceof Map<?, ?> root)) {
            throw new IllegalArgumentException("The compatibility manifest must be a mapping");
        }
        if (!Integer.valueOf(MANIFEST_VERSION).equals(root.get("manifestVersion"))) {
            throw new IllegalArgumentException("The compatibility manifest must declare manifestVersion " + MANIFEST_VERSION);
        }
        if (!(root.get("releases") instanceof List<?> entries)) {
            throw new IllegalArgumentException("The compatibility manifest must list its releases");
        }
        Map<String, Release> releases = new LinkedHashMap<>();
        for (Object entry : entries) {
            Release release = parseRelease(entry);
            if (releases.put(release.aresVersion(), release) != null) {
                throw new IllegalArgumentException("The compatibility manifest lists Ares " + release.aresVersion() + " twice");
            }
        }
        return new AresCompatibilityManifest(Collections.unmodifiableMap(releases));
    }

    private static Release parseRelease(@Nullable Object entry) {
        if (!(entry instanceof Map<?, ?> release) || !(release.get("aresVersion") instanceof String aresVersion) || aresVersion.isBlank()) {
            throw new IllegalArgumentException("Every release of the compatibility manifest needs an aresVersion");
        }
        if (!(release.get("policyFormatVersions") instanceof Map<?, ?> formatVersions) || !(formatVersions.get("minimum") instanceof Integer minimum)
                || !(formatVersions.get("maximum") instanceof Integer maximum) || minimum < 1 || maximum < minimum) {
            throw new IllegalArgumentException("Ares " + aresVersion + " needs policyFormatVersions with a minimum of at least 1 and a maximum not below it");
        }
        if (!(release.get("fields") instanceof List<?> fieldEntries) || fieldEntries.isEmpty()) {
            throw new IllegalArgumentException("Ares " + aresVersion + " needs a list of fields");
        }
        Set<String> fields = new LinkedHashSet<>();
        for (Object field : fieldEntries) {
            if (!(field instanceof String path) || path.isBlank() || path.startsWith(".") || path.endsWith(".")) {
                throw new IllegalArgumentException("Ares " + aresVersion + " lists an invalid field: " + field);
            }
            fields.add(path);
        }
        return new Release(aresVersion, minimum, maximum, Collections.unmodifiableSet(fields));
    }

    /**
     * @return the releases the manifest lists, by their version
     */
    public Set<String> verifiedVersions() {
        return releases.keySet();
    }

    /**
     * @param aresVersion an Ares release
     * @return what the release accepts; empty if the manifest does not list it
     */
    public Optional<Release> release(String aresVersion) {
        return Optional.ofNullable(releases.get(aresVersion));
    }

    /**
     * Checks whether an Ares release accepts a policy file.
     *
     * @param aresVersion the release to pin
     * @param policy      the content of the policy file
     * @return the result; {@link Verdict#UNVERIFIED} if the manifest does not list the release or the file cannot be read as a policy, because then another
     *         check reports the problem
     */
    public Check check(String aresVersion, String policy) {
        Optional<Release> release = release(aresVersion);
        if (release.isEmpty()) {
            return Check.UNVERIFIED;
        }
        Optional<Object> tree = SecurityPolicySchema.readTree(policy);
        if (tree.isEmpty() || !(tree.get() instanceof Map<?, ?> root)) {
            return Check.UNVERIFIED;
        }
        Set<String> unsupportedFields = new LinkedHashSet<>();
        collectUnsupportedFields(root, "", release.get().fields(), unsupportedFields);
        BigInteger unsupportedFormatVersion = null;
        if (root.get(FORMAT_VERSION_KEY) instanceof BigInteger formatVersion && (formatVersion.compareTo(BigInteger.valueOf(release.get().minimumFormatVersion())) < 0
                || formatVersion.compareTo(BigInteger.valueOf(release.get().maximumFormatVersion())) > 0)) {
            unsupportedFormatVersion = formatVersion;
        }
        if (unsupportedFields.isEmpty() && unsupportedFormatVersion == null) {
            return Check.COMPATIBLE;
        }
        return new Check(Verdict.INCOMPATIBLE, List.copyOf(unsupportedFields), unsupportedFormatVersion);
    }

    /**
     * Walks the policy and records every field the release does not accept. The fields below an unknown field are not recorded, because the outermost one
     * already tells the instructor what to remove.
     */
    private static void collectUnsupportedFields(Map<?, ?> mapping, String prefix, Set<String> acceptedFields, Set<String> unsupportedFields) {
        for (Map.Entry<?, ?> entry : mapping.entrySet()) {
            String path = prefix + entry.getKey();
            if (!acceptedFields.contains(path)) {
                unsupportedFields.add(path);
                continue;
            }
            collectUnsupportedFieldsOfValue(entry.getValue(), path, acceptedFields, unsupportedFields);
        }
    }

    private static void collectUnsupportedFieldsOfValue(@Nullable Object value, String path, Set<String> acceptedFields, Set<String> unsupportedFields) {
        if (value instanceof Map<?, ?> nested) {
            collectUnsupportedFields(nested, path + ".", acceptedFields, unsupportedFields);
        }
        else if (value instanceof List<?> entries) {
            for (Object listEntry : entries) {
                if (listEntry instanceof Map<?, ?> nested) {
                    collectUnsupportedFields(nested, path + LIST_ENTRY + ".", acceptedFields, unsupportedFields);
                }
            }
        }
    }
}
