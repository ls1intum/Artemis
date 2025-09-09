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
     * Render the textual context for a programming exercise consisting of the problem statement,
     * the template repository and the solution repository, using the latest commit content.
     *
     * @param exercise the programming exercise to render
     * @return a single string containing all rendered sections
     */
    public String renderContext(ProgrammingExercise exercise) {
        if (exercise == null) {
            return "";
        }
        String problemStatement = Objects.requireNonNullElse(exercise.getProblemStatement(), "");
        ProgrammingLanguage programmingLanguage = exercise.getProgrammingLanguage();
        Map<String, String> templateRepository = fetchRepoContents(exercise.getTemplateParticipation() == null ? null : exercise.getTemplateParticipation().getVcsRepositoryUri(),
                "template", exercise.getId());
        Map<String, String> solutionRepository = fetchRepoContents(exercise.getSolutionParticipation() == null ? null : exercise.getSolutionParticipation().getVcsRepositoryUri(),
                "solution", exercise.getId());
        // The test repository will be included in the future

        // Apply language-aware filtering
        templateRepository = languageFilter.filter(templateRepository, programmingLanguage);
        solutionRepository = languageFilter.filter(solutionRepository, programmingLanguage);

        // Render headings + tree + dashed headers + line numbers
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
            return repositoryService.getFilesContentFromBareRepositoryForLastCommit(uri);
        }
        catch (Exception ex) {
            log.warn("Could not fetch {} repository contents for exercise {}", label, exerciseId, ex);
            return Map.of();
        }
    }

    // Render a complete textual snapshot like Python renderer: tree + dashed header + numbered lines
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
            // skip hidden files or any path containing hidden segment
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
        // We need a stable ordered list of all entries: directories first then files
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
