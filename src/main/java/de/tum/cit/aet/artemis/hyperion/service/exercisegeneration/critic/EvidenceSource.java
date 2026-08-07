package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

/**
 * Server-generated, prompt-local evidence IDs over one source document (for example {@code B1} for a brief line and {@code E7} for a specification line).
 * <p>
 * Reviewers cite these IDs instead of copying source text, which is what makes a verdict checkable: the server can decide whether a claim points at text the reviewer was actually
 * shown, and a mis-cited ID degrades to a shorter quote rather than a hallucinated one.
 */
record EvidenceSource(Map<String, String> passages) {

    private static final Pattern NORMATIVE_LANGUAGE = Pattern.compile("\\b(?:must|only|required?|shall|prohibit(?:ed|s)?|never|without)\\b|\\b(?:do|may)\\s+not\\b",
            Pattern.CASE_INSENSITIVE);

    static EvidenceSource from(String prefix, @Nullable String text) {
        Map<String, String> passages = new LinkedHashMap<>();
        if (text != null) {
            int index = 1;
            for (String line : text.lines().toList()) {
                if (!line.isBlank()) {
                    passages.put(prefix + index++, line);
                }
            }
        }
        return new EvidenceSource(Collections.unmodifiableMap(passages));
    }

    String promptText() {
        return passages.entrySet().stream().map(entry -> "[" + entry.getKey() + "] " + entry.getValue()).collect(Collectors.joining("\n"));
    }

    boolean containsAll(@Nullable List<String> evidenceIds) {
        return evidenceIds != null && !evidenceIds.isEmpty() && evidenceIds.stream().allMatch(evidenceId -> evidenceId != null && passages.containsKey(evidenceId))
                && evidenceIds.stream().distinct().count() == evidenceIds.size();
    }

    /**
     * Accepts a grounded citation set when every ID exists and at least one cited passage is substantive. Reviewers often cite a section heading together with its evidence; the
     * heading contributes no authority, while the substantive passage keeps the claim grounded.
     */
    boolean containsSubstantive(@Nullable List<String> evidenceIds) {
        return containsAll(evidenceIds) && evidenceIds.stream().anyMatch(this::isSubstantive);
    }

    List<String> idsUnderHeading(String heading) {
        boolean inSection = false;
        List<String> ids = new java.util.ArrayList<>();
        for (Map.Entry<String, String> passage : passages.entrySet()) {
            String text = passage.getValue().strip();
            if (text.equals(heading)) {
                inSection = true;
                continue;
            }
            if (inSection && text.startsWith("## ")) {
                break;
            }
            if (inSection && isSubstantive(passage.getKey())) {
                ids.add(passage.getKey());
            }
        }
        return List.copyOf(ids);
    }

    private boolean isSubstantive(String evidenceId) {
        String passage = passages.get(evidenceId);
        if (passage == null || passage.strip().startsWith("## ") || isMarkdownTableSeparator(passage)) {
            return false;
        }
        List<String> ids = passages.keySet().stream().toList();
        int index = ids.indexOf(evidenceId);
        return !passage.contains("|") || index < 0 || index + 1 >= ids.size() || !isMarkdownTableSeparator(passages.get(ids.get(index + 1)));
    }

    private static boolean isMarkdownTableSeparator(String passage) {
        String row = passage.strip();
        if (!row.contains("|")) {
            return false;
        }
        boolean hasBoundaryPipe = row.startsWith("|") || row.endsWith("|");
        if (row.startsWith("|")) {
            row = row.substring(1);
        }
        if (row.endsWith("|")) {
            row = row.substring(0, row.length() - 1);
        }
        String[] cells = row.split("\\|", -1);
        return (hasBoundaryPipe || cells.length > 1) && java.util.Arrays.stream(cells).map(String::strip).allMatch(cell -> cell.matches(":?-{3,}:?"));
    }

    String resolve(@Nullable List<String> evidenceIds) {
        // Rendering remains tolerant so audit summaries cannot throw. Each review shape separately validates the evidence IDs that authorize a finding.
        if (evidenceIds == null) {
            return "";
        }
        return evidenceIds.stream().map(passages::get).filter(Objects::nonNull).map(String::strip).collect(Collectors.joining("\"; \""));
    }

    /**
     * Whether every cited passage is descriptive student reasoning rather than a requirement. The concept prompt makes this field non-normative, but a reviewer can still mistake
     * an illustrative control flow for a required implementation form, so admission enforces that prompt contract deterministically here.
     */
    boolean citesOnlyNonNormativeStudentReasoning(@Nullable List<String> evidenceIds) {
        return containsAll(evidenceIds) && evidenceIds.stream().map(passages::get)
                .allMatch(passage -> passage != null && passage.strip().startsWith("Student-owned reasoning:") && !NORMATIVE_LANGUAGE.matcher(passage).find());
    }
}
