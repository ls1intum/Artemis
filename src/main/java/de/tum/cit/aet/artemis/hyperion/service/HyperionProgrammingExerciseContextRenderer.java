package de.tum.cit.aet.artemis.hyperion.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HYPERION;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;

/**
 * Produces a deterministic textual snapshot of a {@link ProgrammingExercise} combining:
 * <ul>
 * <li>Problem statement (as pseudo file problem_statement.md)</li>
 * <li>Filtered template repository</li>
 * <li>Filtered solution repository</li>
 * </ul>
 * Hidden paths (segments starting with '.') are skipped. Repository access failures degrade gracefully to empty sections.
 * <p>
 * <b>Example Output (abridged)</b>
 *
 * <pre>
 * ===== Problem Statement =====
 * --------------------------------------------------------------------------------
 * problem_statement.md:
 * --------------------------------------------------------------------------------
 * 1 | # Implement a Stack
 * 2 | Create a LIFO stack supporting push/pop/peek operations.
 *
 * ===== Template Repository =====
 * template_repository
 * ├── src
 * │   └── main
 * │       └── java
 * │           └── example
 * │               └── Stack.java
 * └── README.md
 *
 * --------------------------------------------------------------------------------
 * template_repository/src/main/java/example/Stack.java:
 * --------------------------------------------------------------------------------
 *  1 | package example;
 *  2 | public class Stack { // TODO student implementation }
 *
 * --------------------------------------------------------------------------------
 * template_repository/README.md:
 * --------------------------------------------------------------------------------
 *  1 | # Stack Exercise
 *  2 | Fill in the missing methods.
 *
 * ===== Solution Repository =====
 * solution_repository
 * └── src
 *     └── main
 *         └── java
 *             └── example
 *                 └── Stack.java
 *
 * --------------------------------------------------------------------------------
 * solution_repository/src/main/java/example/Stack.java:
 * --------------------------------------------------------------------------------
 *  1 | package example;
 *  2 | import java.util.ArrayDeque;
 *  3 | public class Stack { // Full reference implementation }
 * </pre>
 *
 * Lines are always numbered and separated per file with a fixed-width horizontal rule; trees appear only for
 * repositories (not the problem statement pseudo file).
 */
@Component
@Lazy
@Profile(PROFILE_HYPERION)
public class HyperionProgrammingExerciseContextRenderer {

    private static final Logger log = LoggerFactory.getLogger(HyperionProgrammingExerciseContextRenderer.class);

    private final RepositoryService repositoryService;

    private final HyperionProgrammingLanguageContextFilter languageFilter;

    public HyperionProgrammingExerciseContextRenderer(RepositoryService repositoryService, HyperionProgrammingLanguageContextFilter languageFilter) {
        this.repositoryService = repositoryService;
        this.languageFilter = languageFilter;
    }

    /**
     * Render a context snapshot for the exercise. Returns empty string if exercise is null.
     *
     * @param exercise exercise reference
     * @return textual snapshot
     */
    public String renderContext(ProgrammingExercise exercise) {
        if (exercise == null) {
            return "";
        }
        String problemStatement = Objects.requireNonNullElse(exercise.getProblemStatement(), "");
        ProgrammingLanguage language = exercise.getProgrammingLanguage();
        Map<String, String> templateRepository = fetchRepoContents(exercise.getTemplateParticipation() == null ? null : exercise.getTemplateParticipation().getVcsRepositoryUri(),
                "template", exercise.getId());
        Map<String, String> solutionRepository = fetchRepoContents(exercise.getSolutionParticipation() == null ? null : exercise.getSolutionParticipation().getVcsRepositoryUri(),
                "solution", exercise.getId());

        templateRepository = languageFilter.filter(templateRepository, language);
        solutionRepository = languageFilter.filter(solutionRepository, language);

        List<String> parts = new ArrayList<>(3);
        parts.add(renderRepository(Map.of("problem_statement.md", problemStatement), "Problem Statement"));
        parts.add(renderRepository(templateRepository, "Template Repository"));
        parts.add(renderRepository(solutionRepository, "Solution Repository"));
        return String.join("\n\n", parts);
    }

    private Map<String, String> fetchRepoContents(VcsRepositoryUri uri, String label, Long exerciseId) {
        if (uri == null) {
            return Map.of();
        }
        try {
            // Only LocalVC repositories are currently supported for direct bare content retrieval.
            if (uri instanceof LocalVCRepositoryUri localUri) {
                return repositoryService.getFilesContentFromBareRepositoryForLastCommit(localUri);
            }
            // For other VCS types (e.g., remote), we skip to keep Hyperion optional and fail-safe.
            log.debug("Skipping repository content fetch for unsupported VCS URI type {} on exercise {}", uri.getClass().getSimpleName(), exerciseId);
            return Map.of();
        }
        catch (Exception ex) {
            log.warn("Could not fetch {} repository contents for exercise {}", label, exerciseId, ex);
            return Map.of();
        }
    }

    private static String renderRepository(Map<String, String> files, String repoName) {
        final boolean isProblemStatement = Objects.equals(repoName, "Problem Statement");
        final String root = isProblemStatement ? null : (repoName == null ? "repository" : repoName.replace(" ", "_").toLowerCase());
        String treePart = "";
        if (!isProblemStatement) {
            treePart = renderFileStructure(root, files.keySet());
        }
        List<String> fileParts = new ArrayList<>(files.size());
        files.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> fileParts.add(renderFileString(root, e.getKey(), e.getValue())));
        String body = String.join("\n\n", fileParts);
        String headline = "\n===== " + repoName + " =====\n";
        if (!treePart.isEmpty()) {
            return headline + treePart + "\n\n" + body;
        }
        return headline + body;
    }

    private static final class DirNode {

        final TreeMap<String, DirNode> dirs = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        final TreeSet<String> files = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    }

    private static String renderFileStructure(String root, Iterable<String> paths) {
        DirNode rootNode = new DirNode();
        for (String p : paths) {
            if (p == null || p.isBlank()) {
                continue;
            }
            String[] segments = Arrays.stream(p.split("/")).filter(s -> !s.isBlank()).toArray(String[]::new);
            if (Arrays.stream(segments).anyMatch(seg -> seg.startsWith("."))) {
                continue;
            }
            DirNode cursor = rootNode;
            for (int i = 0; i < segments.length; i++) {
                String seg = segments[i];
                boolean last = i == segments.length - 1;
                if (last) {
                    cursor.files.add(seg);
                }
                else {
                    cursor = cursor.dirs.computeIfAbsent(seg, k -> new DirNode());
                }
            }
        }
        List<String> lines = new ArrayList<>();
        if (root != null && !root.isBlank()) {
            lines.add(root);
        }
        collectTree(lines, rootNode, "");
        return String.join("\n", lines);
    }

    private static void collectTree(List<String> lines, DirNode node, String prefix) {
        List<String> dirNames = new ArrayList<>(node.dirs.keySet());
        List<String> fileNames = new ArrayList<>(node.files);
        int total = dirNames.size() + fileNames.size();
        int index = 0;
        for (String dir : dirNames) {
            boolean last = ++index == total;
            lines.add(prefix + (last ? "└── " : "├── ") + dir);
            collectTree(lines, node.dirs.get(dir), prefix + (last ? "    " : "│   "));
        }
        for (String file : fileNames) {
            boolean last = ++index == total;
            lines.add(prefix + (last ? "└── " : "├── ") + file);
        }
    }

    private static final int HR_WIDTH = 80;

    private static String renderFileString(String root, String path, String content) {
        String hr = "-".repeat(HR_WIDTH);
        String header = (root != null && !root.isBlank() ? hr + "\n" + root + "/" + path + ":\n" + hr : hr + "\n" + path + ":\n" + hr);
        if (content == null) {
            content = "";
        }
        List<String> lines = Arrays.asList(content.split("\n", -1));
        int w = Integer.toString(lines.size()).length();
        List<String> out = new ArrayList<>(lines.size() + 1);
        out.add(header);
        for (int i = 0; i < lines.size(); i++) {
            String num = String.format("%" + w + "d", i + 1);
            out.add(num + " | " + lines.get(i));
        }
        return String.join("\n", out);
    }
}
