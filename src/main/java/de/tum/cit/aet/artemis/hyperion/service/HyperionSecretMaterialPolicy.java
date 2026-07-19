package de.tum.cit.aet.artemis.hyperion.service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * Pure, deterministic defense-in-depth policy for high-confidence secret material that must not enter Hyperion provider context or durable generated output.
 * <p>
 * The policy intentionally recognizes only high-confidence paths, containers, private-key armor, and named provider token formats. It does not classify generic assignments,
 * entropy, UUIDs, hashes, JWTs, transformed secrets, or ordinary source-code identifiers. It is not a data-loss-prevention or authorization boundary; administrators must only
 * enable Hyperion for repositories whose contents may be disclosed to the configured model provider.
 */
public final class HyperionSecretMaterialPolicy {

    private static final int MAX_SAFE_PATH_LENGTH = 240;

    private static final Set<String> CREDENTIAL_FILE_NAMES = Set.of(".npmrc", ".pypirc", ".netrc", ".git-credentials", "application_default_credentials.json",
            "service-account.json");

    private static final Set<String> PRIVATE_KEY_FILE_NAMES = Set.of("id_rsa", "id_dsa", "id_ecdsa", "id_ed25519");

    private static final Set<String> PRIVATE_KEY_CONTAINER_EXTENSIONS = Set.of(".key", ".p12", ".pfx", ".jks", ".keystore", ".pkcs12");

    private static final Set<String> CREDENTIAL_PATH_SUFFIXES = Set.of(".aws/credentials", ".azure/accesstokens.json", ".config/gcloud/credentials.db", ".docker/config.json",
            ".kube/config");

    private static final Pattern PEM_PRIVATE_KEY = Pattern.compile("-----BEGIN (?=[A-Z0-9 ._/-]*PRIVATE KEY)[A-Z0-9 ._/-]+-----", Pattern.CASE_INSENSITIVE);

    private static final Pattern AWS_ACCESS_KEY_ID = Pattern.compile("(?<![A-Z0-9])AKIA[A-Z0-9]{16}(?![A-Z0-9])");

    private static final Pattern GITHUB_TOKEN = Pattern.compile("(?<![A-Za-z0-9])gh[pousr]_[A-Za-z0-9]{36,255}(?![A-Za-z0-9])");

    private static final Pattern GITLAB_TOKEN = Pattern.compile("(?<![A-Za-z0-9_-])glpat-[A-Za-z0-9_-]{20,255}(?![A-Za-z0-9_-])");

    private static final Pattern UNSAFE_PATH_CHARACTER = Pattern.compile("[^A-Za-z0-9._/@+-]");

    /** The boundary at which material is assessed. Origins are diagnostic labels only and never weaken classification. */
    public enum Origin {
        WORKSPACE_ARCHIVE, CLASSIC_CONTEXT, PROVIDER_PROMPT, TOOL_OBSERVATION, GENERATED_CANDIDATE, PERSISTENCE
    }

    /** Stable, content-free reason for rejecting material. */
    public enum Category {
        CREDENTIAL_FILE, PRIVATE_KEY_CONTAINER, PEM_PRIVATE_KEY, AWS_ACCESS_KEY_ID, GITHUB_TOKEN, GITLAB_TOKEN
    }

    /** A policy decision containing only safe diagnostic metadata. */
    public record Assessment(String safePath, Origin origin, Optional<Category> category) {

        public boolean isSafe() {
            return category.isEmpty();
        }
    }

    /** Exception whose message contains no matching material. */
    public static final class SecretMaterialException extends RuntimeException {

        private final Assessment assessment;

        private SecretMaterialException(Assessment assessment) {
            super("Hyperion blocked secret material [" + assessment.category().orElseThrow() + "] at " + assessment.safePath());
            this.assessment = assessment;
        }

        public Assessment assessment() {
            return assessment;
        }
    }

    /**
     * Classifies a path and its content without retaining either in the result.
     *
     * @param logicalPath the logical source path
     * @param content     the source bytes
     * @param origin      the boundary performing the assessment
     * @return content-free classification metadata
     */
    public Assessment assess(@Nullable String logicalPath, byte @Nullable [] content, Origin origin) {
        String normalizedPath = normalizePath(logicalPath);
        Category category = classifyPath(normalizedPath);
        if (category == null) {
            category = classifyContent(logicalPath == null ? "" : logicalPath);
        }
        if (category == null) {
            category = classifyContent(content == null ? "" : new String(content, StandardCharsets.UTF_8));
        }
        return new Assessment(safeDiagnosticPath(logicalPath), origin, Optional.ofNullable(category));
    }

    public void requireSafe(@Nullable String logicalPath, byte @Nullable [] content, Origin origin) {
        Assessment assessment = assess(logicalPath, content, origin);
        if (!assessment.isSafe()) {
            throw new SecretMaterialException(assessment);
        }
    }

    /**
     * Returns a complete model-facing replacement, never a partial redaction from which matching material could be reconstructed.
     *
     * @param assessment an unsafe policy decision
     * @return a content-free replacement observation
     */
    public String blockedObservation(Assessment assessment) {
        if (assessment.isSafe()) {
            throw new IllegalArgumentException("A safe assessment does not require a blocked observation");
        }
        return "ERROR: Hyperion blocked secret material [" + assessment.category().orElseThrow() + "] at " + assessment.safePath() + ".";
    }

    private static @Nullable Category classifyPath(String path) {
        String fileName = fileName(path);
        if (fileName.equals(".env") || fileName.startsWith(".env.") || CREDENTIAL_FILE_NAMES.contains(fileName)
                || CREDENTIAL_PATH_SUFFIXES.stream().anyMatch(suffix -> path.equals(suffix) || path.endsWith("/" + suffix))) {
            return Category.CREDENTIAL_FILE;
        }
        if (PRIVATE_KEY_FILE_NAMES.contains(fileName) || PRIVATE_KEY_CONTAINER_EXTENSIONS.stream().anyMatch(fileName::endsWith)) {
            return Category.PRIVATE_KEY_CONTAINER;
        }
        return null;
    }

    private static @Nullable Category classifyContent(String content) {
        if (PEM_PRIVATE_KEY.matcher(content).find()) {
            return Category.PEM_PRIVATE_KEY;
        }
        if (AWS_ACCESS_KEY_ID.matcher(content).find()) {
            return Category.AWS_ACCESS_KEY_ID;
        }
        if (GITHUB_TOKEN.matcher(content).find()) {
            return Category.GITHUB_TOKEN;
        }
        if (GITLAB_TOKEN.matcher(content).find()) {
            return Category.GITLAB_TOKEN;
        }
        return null;
    }

    private static String normalizePath(@Nullable String logicalPath) {
        if (logicalPath == null) {
            return "";
        }
        String normalized = logicalPath.replace('\\', '/').toLowerCase(Locale.ROOT);
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/");
        }
        return normalized;
    }

    private static String fileName(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    private static String safeDiagnosticPath(@Nullable String logicalPath) {
        if (logicalPath == null || logicalPath.isBlank()) {
            return "<unknown>";
        }
        if (classifyContent(logicalPath) != null) {
            return "<redacted-path>";
        }
        String safe = UNSAFE_PATH_CHARACTER.matcher(logicalPath.replace('\\', '/')).replaceAll("_");
        if (safe.length() <= MAX_SAFE_PATH_LENGTH) {
            return safe;
        }
        return safe.substring(0, MAX_SAFE_PATH_LENGTH - 1) + "…";
    }
}
