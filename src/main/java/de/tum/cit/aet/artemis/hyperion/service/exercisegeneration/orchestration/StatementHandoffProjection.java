package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.GeneratedTestPlan;

/**
 * Projects the accepted grading plan and the server-authored structural oracle into the only facts statement authoring needs. Raw build output and executable-build instructions
 * are excluded: they are debugging context, not student-facing contract evidence.
 * <p>
 * Pure by construction — it takes the already-read plan document rather than the sandbox — so the projection can be reasoned about and tested without a session.
 */
final class StatementHandoffProjection {

    private static final Logger log = LoggerFactory.getLogger(StatementHandoffProjection.class);

    private StatementHandoffProjection() {
    }

    /**
     * Renders the handoff block appended to the statement stage's user prompt.
     *
     * @param planJson                  the accepted {@code test-plan.json} document, or blank when there is none
     * @param seededStructuralTestNames the server-seeded structural check names, which are visible and must also be bound
     * @return the handoff block, or the empty string when no usable plan is available
     */
    static String project(String planJson, Set<String> seededStructuralTestNames) {
        if (planJson.isBlank()) {
            return "";
        }
        try {
            GeneratedTestPlan plan = GeneratedTestPlan.parse(planJson);
            StringBuilder handoff = new StringBuilder("=== ACCEPTED STATEMENT HANDOFF ===\n");
            handoff.append("Use the exact lowercase singular Artemis task syntax `[task][Student-facing title](exactTestName)`. Bind every visible test below exactly once on "
                    + "the one task for its specification seam; a task may list multiple names separated by commas. Any testsColor links must use only these same exact test "
                    + "method names. Never use `[tasks]`, `[Task]`, display names, or hidden test names. Write each task marker as plain Markdown on its own line, without inline "
                    + "backticks or a fenced code block.\nVisible tests grouped by specification seam:\n");
            plan.visibleEntries().stream()
                    .collect(Collectors.groupingBy(GeneratedTestPlan.Entry::seam, LinkedHashMap::new,
                            Collectors.mapping(GeneratedTestPlan.Entry::name, Collectors.toCollection(ArrayList::new))))
                    .forEach((seam, names) -> handoff.append("- ").append(seam).append(": ").append(String.join(", ", names)).append("\n"));
            if (!seededStructuralTestNames.isEmpty()) {
                handoff.append("Server-seeded structural checks grouped by owner type (all are visible and must also be bound exactly once):\n");
                structuralTestsByOwner(seededStructuralTestNames)
                        .forEach((owner, names) -> handoff.append("- ").append(owner).append(": ").append(String.join(", ", names)).append("\n"));
                handoff.append("Add each structural name to the existing task whose work creates or declares that owner type/API; do not create one task per structural check. "
                        + "If several behavioral seams share the owner, attach the checks to the task that introduces the type/API and never duplicate them. These checks may "
                        + "carry zero score when behavioral evidence exists, but they are still visible Artemis progress checks for required student-created structure.\n");
            }
            if (!plan.hiddenEntries().isEmpty()) {
                handoff.append(plan.hiddenEntries().size()).append(
                        " hidden behavioral test(s) are intentionally omitted from this handoff. Bind only the visible names above; do not inspect or reveal hidden names.\n");
            }
            handoff.append("Write the complete student-facing artifact with write_file(\"problem-statement.md\", ...). A prose chat response does not create the artifact.\n")
                    .append("=== END ACCEPTED STATEMENT HANDOFF ===");
            return handoff.toString();
        }
        catch (IllegalArgumentException e) {
            log.warn("Could not project the accepted test plan into the statement handoff: {}", e.getMessage());
            return "";
        }
    }

    /** Groups authoritative Ares names by the type inside their brackets while retaining every name even if a future provider uses another shape. */
    private static Map<String, List<String>> structuralTestsByOwner(Set<String> seededStructuralTestNames) {
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        seededStructuralTestNames.stream().sorted().forEach(name -> {
            int openBracket = name.indexOf('[');
            int closeBracket = name.lastIndexOf(']');
            String owner = openBracket >= 0 && closeBracket > openBracket + 1 ? name.substring(openBracket + 1, closeBracket) : "Other structural checks";
            grouped.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(name);
        });
        return grouped;
    }
}
