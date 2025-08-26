package de.tum.cit.aet.artemis.hyperion.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HYPERION;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Pattern;

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
public class ProgrammingExerciseContextRenderer {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseContextRenderer.class);

    private final RepositoryService repositoryService;

    private final ProgrammingLanguageContextFilter languageFilter;

    public ProgrammingExerciseContextRenderer(RepositoryService repositoryService, ProgrammingLanguageContextFilter languageFilter) {
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
        String problem = Objects.requireNonNullElse(exercise.getProblemStatement(), "");
        ProgrammingLanguage language = exercise.getProgrammingLanguage();

        // Fetch latest contents (maps path->content)
        Map<String, String> template = Map.of();
        Map<String, String> solution = Map.of();
        try {
            if (exercise.getTemplateParticipation() != null) {
                VcsRepositoryUri uri = exercise.getTemplateParticipation().getVcsRepositoryUri();
                if (uri != null) {
                    template = repositoryService.getFilesContentFromBareRepositoryForLastCommit(uri);
                }
            }
        }
        catch (Exception ex) {
            log.warn("Could not fetch template repository contents for exercise {}: {}", exercise.getId(), ex.getMessage());
        }
        try {
            if (exercise.getSolutionParticipation() != null) {
                VcsRepositoryUri uri = exercise.getSolutionParticipation().getVcsRepositoryUri();
                if (uri != null) {
                    solution = repositoryService.getFilesContentFromBareRepositoryForLastCommit(uri);
                }
            }
        }
        catch (Exception ex) {
            log.warn("Could not fetch solution repository contents for exercise {}: {}", exercise.getId(), ex.getMessage());
        }

        // Apply language-aware filtering
        template = languageFilter.filter(template, language);
        solution = languageFilter.filter(solution, language);

        // Render exactly like Python (headings + tree + dashed headers + line numbers). No tests section here.
        List<String> parts = new ArrayList<>(3);
        parts.add(renderRepository(Map.of("problem_statement.md", problem), "Problem Statement"));
        parts.add(renderRepository(template, "Template Repository"));
        parts.add(renderRepository(solution, "Solution Repository"));
        return String.join("\n\n", parts);
    }

    // Render a complete textual snapshot like Python renderer: tree + dashed header + numbered lines
    private static String renderRepository(Map<String, String> files, String repoName) {
        return renderRepository(files, "/", false, repoName, 80);
    }

    private static String renderRepository(Map<String, String> files, String sep, boolean showHidden, String repoName, int width) {
        boolean isProblemStatement = Objects.equals(repoName, "Problem Statement");
        String root = isProblemStatement ? null : (repoName == null ? "repository" : repoName.replace(" ", "_").toLowerCase());

        String treePart = "";
        if (!isProblemStatement) {
            List<String> paths = new ArrayList<>(files.keySet());
            treePart = renderFileStructure(root, paths, sep, showHidden);
        }

        List<String> fileParts = new ArrayList<>();
        // Sort by path
        TreeMap<String, String> sorted = new TreeMap<>(String::compareTo);
        sorted.putAll(files);
        for (var e : sorted.entrySet()) {
            fileParts.add(renderFileString(root, e.getKey(), e.getValue(), true, width));
        }
        String body = String.join("\n\n", fileParts);

        if (repoName != null && !repoName.isBlank()) {
            String headline = "\n===== " + repoName + " =====\n";
            if (!treePart.isEmpty()) {
                return headline + treePart + "\n\n" + body;
            }
            else {
                return headline + body;
            }
        }

        if (!treePart.isEmpty()) {
            return treePart + "\n\n" + body;
        }
        else {
            return body;
        }
    }

    private static String renderFileStructure(String root, List<String> paths, String sep, boolean showHidden) {
        Map<String, Object> tree = new LinkedHashMap<>();
        for (String p : paths) {
            if (p == null || p.isBlank()) {
                continue;
            }
            String[] parts = Arrays.stream(p.split(Pattern.quote(sep))).filter(s -> !s.isBlank()).toArray(String[]::new);
            if (!showHidden && Arrays.stream(parts).anyMatch(part -> part.startsWith("."))) {
                continue;
            }
            Map<String, Object> node = tree;
            for (String part : parts) {
                Object child = node.get(part);
                if (!(child instanceof Map)) {
                    child = new LinkedHashMap<String, Object>();
                    node.put(part, child);
                }
                // noinspection unchecked
                node = (Map<String, Object>) child;
            }
        }

        List<String> lines = new ArrayList<>();
        if (root != null && !root.isBlank()) {
            lines.add(root);
        }

        collectTree(lines, tree, "");
        return String.join("\n", lines);
    }

    @SuppressWarnings("unchecked")
    private static void collectTree(List<String> lines, Map<String, Object> subtree, String prefix) {
        List<Map.Entry<String, Object>> items = new ArrayList<>(subtree.entrySet());
        // Directories first (value is a non-empty map), then by case-insensitive name
        items.sort((a, b) -> {
            boolean aIsDir = a.getValue() instanceof Map && !((Map<String, Object>) a.getValue()).isEmpty();
            boolean bIsDir = b.getValue() instanceof Map && !((Map<String, Object>) b.getValue()).isEmpty();
            if (aIsDir != bIsDir) {
                return aIsDir ? -1 : 1;
            }
            return a.getKey().toLowerCase().compareTo(b.getKey().toLowerCase());
        });

        for (int i = 0; i < items.size(); i++) {
            var e = items.get(i);
            String name = e.getKey();
            Object children = e.getValue();
            boolean last = i == items.size() - 1;
            String connector = last ? "└── " : "├── ";
            lines.add(prefix + connector + name);
            if (children instanceof Map<?, ?> childMap && !childMap.isEmpty()) {
                String extension = last ? "    " : "│   ";
                collectTree(lines, (Map<String, Object>) childMap, prefix + extension);
            }
        }
    }

    private static String renderFileString(String root, String path, String content, boolean lineNumbers, int width) {
        String hr = "-".repeat(Math.max(0, width));
        String header = (root != null && !root.isBlank() ? hr + "\n" + root + "/" + path + ":\n" + hr : hr + "\n" + path + ":\n" + hr);

        if (content == null) {
            content = "";
        }

        List<String> lines = Arrays.asList(content.split("\n", -1));
        List<String> out = new ArrayList<>(lines.size());
        if (lineNumbers) {
            int w = Integer.toString(lines.size()).length();
            for (int i = 0; i < lines.size(); i++) {
                String num = String.format("%" + w + "d", i + 1);
                out.add(num + " | " + lines.get(i));
            }
        }
        else {
            out.addAll(lines);
        }
        List<String> all = new ArrayList<>(out.size() + 1);
        all.add(header);
        all.addAll(out);
        return String.join("\n", all);
    }
}
