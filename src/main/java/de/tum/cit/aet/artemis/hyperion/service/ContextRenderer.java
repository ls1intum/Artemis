package de.tum.cit.aet.artemis.hyperion.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;

/**
 * Renders repository context exactly like the Python renderer in Hyperion.
 * Produces a tree view followed by each file with a dashed header and line numbers.
 */
public final class ContextRenderer {

    private ContextRenderer() {
    }

    public record RepoFile(String path, String content) {
    }

    /**
     * Render a complete textual snapshot of a repository: first the tree, then the contents of every file.
     * If repoName equals "Problem Statement", the tree is omitted and the content is rendered as problem_statement.md.
     */
    public static String renderRepository(List<RepoFile> files, String repoName) {
        return renderRepository(files, "/", false, repoName, 80);
    }

    public static String renderRepository(List<RepoFile> files, String sep, boolean showHidden, String repoName, int width) {
        boolean isProblemStatement = Objects.equals(repoName, "Problem Statement");
        String root = isProblemStatement ? null : (repoName == null ? "repository" : repoName.replace(" ", "_").toLowerCase());

        String treePart = "";
        if (!isProblemStatement) {
            List<String> paths = files.stream().map(RepoFile::path).toList();
            treePart = renderFileStructure(root, paths, sep, showHidden);
        }

        List<String> fileParts = new ArrayList<>();
        files.stream().sorted(java.util.Comparator.comparing(RepoFile::path)).forEach(f -> fileParts.add(renderFileString(root, f.path, f.content, true, width)));
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

    public static String renderFileStructure(String root, List<String> paths, String sep, boolean showHidden) {
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

    public static String renderFileString(String root, String path, String content, boolean lineNumbers, int width) {
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

    /**
     * Apply simple language-aware file filtering. For Java, include only .java files under any src directory and skip hidden paths.
     */
    public static List<RepoFile> filterFilesByLanguage(List<RepoFile> files, ProgrammingLanguage language) {
        if (language == null) {
            return files;
        }
        if (language == ProgrammingLanguage.JAVA) {
            List<RepoFile> result = new ArrayList<>();
            for (RepoFile f : files) {
                String p = f.path();
                if (p == null || !p.endsWith(".java")) {
                    continue;
                }
                List<String> parts = Arrays.asList(p.split("/"));
                if (parts.stream().noneMatch("src"::equals)) {
                    continue;
                }
                if (parts.stream().anyMatch(s -> s.startsWith("."))) {
                    continue;
                }
                // Rough max size 50KB similar to Python config
                if (f.content() != null && f.content().length() > 50 * 1024) {
                    continue;
                }
                result.add(f);
            }
            return result;
        }
        // Default: return unchanged
        return files;
    }
}
