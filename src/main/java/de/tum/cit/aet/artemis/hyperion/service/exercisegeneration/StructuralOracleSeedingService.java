package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.core.service.TempFileUtilService;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.service.structureoraclegenerator.OracleGenerator;

/**
 * Adds Ares structural tests to a generated Java exercise, the same way a manually authored exercise gets them: it runs the deterministic {@link OracleGenerator} over the agent's
 * produced solution and template, and — only when their structures actually differ — seeds the resulting {@code test.json} structure oracle plus the four boilerplate structural
 * test classes ({@code ClassTest}/{@code MethodTest}/{@code AttributeTest}/{@code ConstructorTest}) into the test repository, so the verifier compiles and runs them alongside the
 * behavioural tests.
 * <p>
 * This is intentionally conservative and non-regressive: structural tests are seeded ONLY for a {@code public} class the student must create — one present in the solution but
 * entirely absent from the template — and even then only its public/protected surface is enforced. Incidental differences (a private helper the template stub did not replicate, a
 * stubbed body, a package-private utility, or a missing method within a class that exists in both) are filtered out, so a correct behaviour-only exercise — the common case — is
 * never burdened with spurious structural requirements. Any failure here is logged and skipped: a structural-oracle problem must never abort an otherwise valid generation.
 */
@Lazy
@Service
@Conditional(HyperionEnabled.class)
public class StructuralOracleSeedingService {

    private static final Logger log = LoggerFactory.getLogger(StructuralOracleSeedingService.class);

    private static final Duration CLEANUP_TIMEOUT = Duration.ofSeconds(30);

    private static final String STRUCTURAL_RESOURCE_DIR = "templates/java/test/testFiles/structural/";

    private static final List<String> STRUCTURAL_CLASSES = List.of("ClassTest.java", "MethodTest.java", "AttributeTest.java", "ConstructorTest.java");

    private static final String ORACLE_FILE = "test.json";

    private static final String PACKAGE_PLACEHOLDER = "${packageName}";

    private static final Pattern PACKAGE_DECLARATION = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Matches a top-level type declaration so we can tell whether a class exists in a source set and whether the solution declares it {@code public}. */
    private static final Pattern TYPE_DECLARATION = Pattern
            .compile("(?m)(?:^|\\s)(public\\s+)?(?:abstract\\s+|final\\s+|sealed\\s+|non-sealed\\s+|strictfp\\s+)*(?:class|interface|enum|record)\\s+(\\w+)");

    private final GenerationWorkspaceService workspace;

    private final TempFileUtilService tempFileUtilService;

    public StructuralOracleSeedingService(GenerationWorkspaceService workspace, TempFileUtilService tempFileUtilService) {
        this.workspace = workspace;
        this.tempFileUtilService = tempFileUtilService;
    }

    /**
     * Generates the structure oracle from the agent's solution and template and seeds the structural tests when the structures differ. Java only; a no-op for other languages and
     * on any error.
     * <p>
     * <strong>Returns the AUTHORITATIVE set of structural test-case names this call actually seeded</strong> ({@code testClass[X]}, {@code testMethods[X]},
     * {@code testAttributes[X]},
     * {@code testConstructors[X]} for each class {@code X} kept in the filtered oracle), computed deterministically from the oracle THIS service generated — not from anything the
     * agent wrote. The verifier consumes this set so it can treat these auto-injected machinery tests as not requiring a problem-statement {@code [task]} binding to resolve, while
     * still subjecting them to the pass-on-solution / fail-on-template differential. Because the set is derived from the seeder's own oracle (never from agent-supplied names or a
     * name pattern the agent could mimic), the agent cannot grow it to smuggle a real behaviour test into the binding exemption. Empty when nothing was seeded (behaviour-only
     * exercise, non-Java, or any error).
     *
     * @param sandbox   the live sandbox session holding the produced files
     * @param sessionId the session id
     * @param exercise  the exercise being generated
     * @return the exact structural test-case names seeded this run (empty when none)
     */
    public Set<String> seedIfStructuralDiff(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise) {
        if (exercise.getProgrammingLanguage() != ProgrammingLanguage.JAVA) {
            return Set.of();
        }
        Path solutionDir = null;
        Path templateDir = null;
        try {
            Map<String, String> solutionFiles = workspace.extractRepositoryFiles(sandbox, sessionId, RepositoryType.SOLUTION);
            Map<String, String> templateFiles = workspace.extractRepositoryFiles(sandbox, sessionId, RepositoryType.TEMPLATE);
            Map<String, String> testFiles = workspace.extractRepositoryFiles(sandbox, sessionId, RepositoryType.TESTS);
            if (solutionFiles.isEmpty() || templateFiles.isEmpty()) {
                return Set.of();
            }
            String testDirectory = locateTestSourceDirectory(testFiles);
            if (testDirectory == null) {
                return Set.of();
            }

            solutionDir = materialize(solutionFiles, "hyperion-oracle-solution-");
            templateDir = materialize(templateFiles, "hyperion-oracle-template-");
            // Restrict the structure oracle to the genuine structural contract: only classes the student must CREATE (a public class present in the solution but entirely absent
            // from the template), and within them only the public/protected surface. This keeps incidental differences — a private helper the template stub did not replicate, a
            // stubbed body, a package-private utility — from becoming spurious structural requirements that would reject an otherwise-correct behaviour-only exercise.
            String oracle = filterOracleToCreatedPublicApi(OracleGenerator.generateStructureOracleJSON(solutionDir, templateDir), templateFiles, solutionFiles);

            if (isStructurallyEmpty(oracle)) {
                // No public class is missing from the template: this is a behaviour-only exercise. Remove any oracle/classes a previous attempt seeded so nothing stale lingers.
                cleanupStructuralFiles(sandbox, sessionId, testDirectory);
                return Set.of();
            }

            String packageName = parsePackage(solutionFiles);
            Map<String, String> seededFiles = new LinkedHashMap<>();
            String dirPrefix = GenerationWorkspaceService.directoryFor(RepositoryType.TESTS) + "/" + testDirectory;
            seededFiles.put(dirPrefix + "/" + ORACLE_FILE, oracle);
            for (String className : STRUCTURAL_CLASSES) {
                seededFiles.put(dirPrefix + "/" + className, structuralClassContent(className, packageName));
            }
            sandbox.copyIn(sessionId, GenerationWorkspaceService.WORKSPACE, WorkspaceArchive.buildWorkspaceTarStream(seededFiles, Map.of()));
            Set<String> seededTestNames = structuralTestNames(oracle);
            log.info("Seeded {} structural test classes and a structure oracle for exercise {} (package '{}'), structural test names {}", STRUCTURAL_CLASSES.size(),
                    exercise.getId(), packageName, seededTestNames);
            return seededTestNames;
        }
        catch (RuntimeException | IOException e) {
            log.warn("Could not seed structural tests for exercise {}: {}", exercise.getId(), e.getMessage());
            return Set.of();
        }
        finally {
            deleteQuietly(solutionDir);
            deleteQuietly(templateDir);
        }
    }

    /**
     * The exact Ares dynamic-test names the four structural test classes will report at runtime for the classes in the (already-filtered) oracle. Ares' {@code ClassTestProvider}
     * etc. name each generated {@link org.junit.jupiter.api.DynamicTest} {@code testClass[<ClassName>]} / {@code testMethods[<ClassName>]} / {@code testAttributes[<ClassName>]} /
     * {@code testConstructors[<ClassName>]} (one per class entry in {@code test.json}). We reconstruct that exact name set here, from the class names in the oracle THIS service
     * produced, so the verifier's exemption is keyed to a forgery-resistant authority (the seeder's own oracle) rather than a name pattern the agent could imitate.
     */
    private static Set<String> structuralTestNames(String oracle) throws IOException {
        Set<String> names = new HashSet<>();
        for (JsonNode entry : (ArrayNode) MAPPER.readTree(oracle)) {
            String className = entry.path("class").path("name").asText("");
            if (className.isEmpty()) {
                continue;
            }
            names.add("testClass[" + className + "]");
            names.add("testMethods[" + className + "]");
            names.add("testAttributes[" + className + "]");
            names.add("testConstructors[" + className + "]");
        }
        return names;
    }

    /**
     * Locates the directory (relative to the test repository root) that contains the agent's behaviour test sources, so the structural tests are placed in the same source set and
     * package. Returns the directory of the first {@code .java} test file, or {@code null} if the agent wrote no Java test file.
     */
    private static String locateTestSourceDirectory(Map<String, String> testFiles) {
        return testFiles.keySet().stream().filter(path -> path.endsWith(".java") && path.contains("/")).map(path -> path.substring(0, path.lastIndexOf('/'))).findFirst()
                .orElse(null);
    }

    private Path materialize(Map<String, String> files, String prefix) throws IOException {
        Path dir = tempFileUtilService.createTempDirectory(prefix);
        for (Map.Entry<String, String> entry : files.entrySet()) {
            // qdox only needs the .java sources; writing the rest is harmless but skipped to keep the temp tree small.
            if (!entry.getKey().endsWith(".java")) {
                continue;
            }
            Path target = dir.resolve(entry.getKey()).normalize();
            if (!target.startsWith(dir)) {
                continue;
            }
            Files.createDirectories(target.getParent());
            FileUtils.writeStringToFile(target.toFile(), entry.getValue(), StandardCharsets.UTF_8);
        }
        return dir;
    }

    private String structuralClassContent(String className, String packageName) throws IOException {
        String content = new String(new ClassPathResource(STRUCTURAL_RESOURCE_DIR + className).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (packageName.isEmpty()) {
            // Default package: drop the package declaration line entirely.
            return content.replaceFirst("(?m)^\\s*package\\s+" + Pattern.quote(PACKAGE_PLACEHOLDER) + "\\s*;\\s*\\n", "");
        }
        return content.replace(PACKAGE_PLACEHOLDER, packageName);
    }

    private static String parsePackage(Map<String, String> solutionFiles) {
        for (Map.Entry<String, String> entry : solutionFiles.entrySet()) {
            if (!entry.getKey().endsWith(".java")) {
                continue;
            }
            Matcher matcher = PACKAGE_DECLARATION.matcher(entry.getValue());
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return "";
    }

    /**
     * Filters the raw structure oracle to the genuine structural contract: entries whose class is a {@code public} type in the solution but is entirely absent from the template
     * (a class the student must create), keeping only their public/protected members. Returns {@code []} when nothing qualifies.
     */
    static String filterOracleToCreatedPublicApi(String oracle, Map<String, String> templateFiles, Map<String, String> solutionFiles) throws IOException {
        if (isStructurallyEmpty(oracle)) {
            return "[]";
        }
        Set<String> templateTypes = declaredTypes(templateFiles, false);
        Set<String> publicSolutionTypes = declaredTypes(solutionFiles, true);
        ArrayNode result = MAPPER.createArrayNode();
        for (JsonNode entry : (ArrayNode) MAPPER.readTree(oracle)) {
            String className = entry.path("class").path("name").asText("");
            // Keep only public classes the student must create (in the solution as a public type, missing from the template entirely).
            if (className.isEmpty() || templateTypes.contains(className) || !publicSolutionTypes.contains(className)) {
                continue;
            }
            ObjectNode kept = ((ObjectNode) entry).deepCopy();
            stripNonPublicMembers(kept, "methods");
            stripNonPublicMembers(kept, "attributes");
            stripNonPublicMembers(kept, "constructors");
            result.add(kept);
        }
        return result.isEmpty() ? "[]" : MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(result);
    }

    /** Drops members whose modifiers do not include {@code public} or {@code protected}; private/package-private members are implementation details, not part of the contract. */
    private static void stripNonPublicMembers(ObjectNode classEntry, String field) {
        if (!(classEntry.get(field) instanceof ArrayNode members)) {
            return;
        }
        ArrayNode kept = MAPPER.createArrayNode();
        for (JsonNode member : members) {
            JsonNode modifiers = member.path("modifiers");
            boolean isPublicApi = false;
            if (modifiers.isArray()) {
                for (JsonNode modifier : modifiers) {
                    if ("public".equals(modifier.asText()) || "protected".equals(modifier.asText())) {
                        isPublicApi = true;
                        break;
                    }
                }
            }
            if (isPublicApi) {
                kept.add(member);
            }
        }
        classEntry.set(field, kept);
    }

    /** The simple names of top-level types declared across the given sources; when {@code publicOnly}, only types declared {@code public}. */
    private static Set<String> declaredTypes(Map<String, String> files, boolean publicOnly) {
        Set<String> names = new HashSet<>();
        for (String content : files.values()) {
            Matcher matcher = TYPE_DECLARATION.matcher(content);
            while (matcher.find()) {
                if (!publicOnly || matcher.group(1) != null) {
                    names.add(matcher.group(2));
                }
            }
        }
        return names;
    }

    /** An oracle with no class diffs serialises to an empty JSON array; treat that as "nothing to seed". */
    static boolean isStructurallyEmpty(String oracle) {
        if (oracle == null) {
            return true;
        }
        String trimmed = oracle.trim();
        return trimmed.isEmpty() || trimmed.equals("[]") || trimmed.equals("[ ]");
    }

    private void cleanupStructuralFiles(InteractiveSandbox sandbox, String sessionId, String testDirectory) {
        String dir = GenerationWorkspaceService.WORKSPACE + "/" + GenerationWorkspaceService.directoryFor(RepositoryType.TESTS) + "/" + testDirectory;
        StringBuilder command = new StringBuilder("rm -f");
        command.append(" \"").append(dir).append("/").append(ORACLE_FILE).append("\"");
        for (String className : STRUCTURAL_CLASSES) {
            command.append(" \"").append(dir).append("/").append(className).append("\"");
        }
        try {
            sandbox.exec(sessionId, CLEANUP_TIMEOUT, "sh", "-c", command.toString());
        }
        catch (RuntimeException e) {
            log.debug("Structural cleanup command failed (harmless): {}", e.getMessage());
        }
    }

    private static void deleteQuietly(Path dir) {
        if (dir != null) {
            FileUtils.deleteQuietly(dir.toFile());
        }
    }
}
