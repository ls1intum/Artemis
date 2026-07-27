package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

/**
 * Server-generated, prompt-local evidence IDs over one source document (for example {@code B1} for a brief line and {@code E7} for a specification line).
 * <p>
 * Reviewers cite these IDs instead of copying source text, which is what makes a verdict checkable: the server can decide whether a claim points at text the reviewer was actually
 * shown, and a mis-cited ID degrades to a shorter quote rather than a hallucinated one.
 */
record EvidenceSource(Map<String, String> passages) {

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

    boolean containsSubstantive(@Nullable List<String> evidenceIds) {
        return containsAll(evidenceIds) && evidenceIds.stream().map(passages::get).anyMatch(passage -> passage != null && !passage.strip().startsWith("## "));
    }

    String resolve(@Nullable List<String> evidenceIds) {
        // Tolerant of missing or unknown IDs: evidence citation is advisory grounding, not a terminal contract. A mis-cited ID
        // resolves to a shorter quote rather than throwing, so a good verdict is never discarded over a self-report slip.
        if (evidenceIds == null) {
            return "";
        }
        return evidenceIds.stream().map(passages::get).filter(Objects::nonNull).map(String::strip).collect(Collectors.joining("\"; \""));
    }
}
