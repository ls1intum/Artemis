package de.tum.cit.aet.artemis.atlas.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.atlas.dto.CompetencyIndexDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyIndexResponseDTO;
import de.tum.cit.aet.artemis.atlas.service.util.AtlasPromptSanitizer;

/**
 * Renders the instructor-controlled parts of the Atlas orchestrator execute prompt: the numbered change batch of
 * exercises and lecture units, and the competency index tree. Every instructor string is fence-sanitized and
 * length-capped here, so {@link CompetencyOrchestrationService} and the read tools share one set of prompt caps.
 * Stateless; all methods are pure functions of their inputs.
 */
final class OrchestratorPromptRenderer {

    /** Length caps on instructor-controlled strings to bound prompt size and injection surface. */
    static final int EXERCISE_TITLE_MAX = 200;

    static final int PROBLEM_STATEMENT_MAX = 16_000;

    static final int COMPETENCY_TITLE_MAX = 200;

    static final int LECTURE_UNIT_NAME_MAX = 200;

    static final int TYPE_LABEL_MAX = 50;

    static final int LECTURE_UNIT_METADATA_VALUE_MAX = 1_000;

    private OrchestratorPromptRenderer() {
    }

    /**
     * Renders the combined change batch: exercise entries ({@code [UPDATE exercise id=..]}) first, then
     * lecture-unit entries ({@code [UPDATE lecture-unit id=..]}), sharing one continuous 1-based numbering
     * so the LLM sees a single ordered list. All instructor text is fence-sanitized and length-capped.
     */
    static String renderChangeBatch(List<ExerciseChange> exerciseChanges, List<LectureUnitChange> lectureUnitChanges) {
        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (ExerciseChange change : exerciseChanges) {
            String safeTitle = sanitizeForPrompt(change.title(), EXERCISE_TITLE_MAX);
            String safeBody = change.problemStatement() == null || change.problemStatement().isBlank() ? "(no problem statement available)"
                    : sanitizeForPrompt(change.problemStatement(), PROBLEM_STATEMENT_MAX);
            if (index > 1) {
                sb.append("\n\n");
            }
            sb.append(index).append(". [UPDATE exercise id=").append(change.exerciseId()).append("] ").append(safeTitle).append('\n').append(safeBody);
            index++;
        }
        for (LectureUnitChange change : lectureUnitChanges) {
            String safeTitle = sanitizeForPrompt(change.title(), LECTURE_UNIT_NAME_MAX);
            String safeBody = change.learningText() == null || change.learningText().isBlank() ? "(no learning text available)"
                    : sanitizeForPrompt(change.learningText(), PROBLEM_STATEMENT_MAX);
            if (index > 1) {
                sb.append("\n\n");
            }
            sb.append(index).append(". [UPDATE lecture-unit id=").append(change.lectureUnitId()).append("] ").append(safeTitle).append('\n').append(safeBody);
            String metadata = renderLectureUnitMetadata(change.metadata());
            if (!metadata.isBlank()) {
                sb.append("\nSource metadata: ").append(metadata);
            }
            index++;
        }
        return sb.toString();
    }

    /** One extracted exercise change rendered as a numbered entry in the change batch block. */
    record ExerciseChange(long exerciseId, String title, @Nullable String problemStatement) {
    }

    /** One extracted lecture-unit change rendered as a numbered entry in the change batch block. */
    record LectureUnitChange(long lectureUnitId, String title, @Nullable String learningText, Map<String, String> metadata) {
    }

    private static String renderLectureUnitMetadata(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "";
        }
        return metadata.entrySet().stream().filter(entry -> !"lectureUnitType".equals(entry.getKey())).filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                .map(entry -> sanitizeForPrompt(entry.getKey(), TYPE_LABEL_MAX) + "=" + sanitizeForPrompt(entry.getValue(), LECTURE_UNIT_METADATA_VALUE_MAX))
                .reduce((left, right) -> left + ", " + right).orElse("");
    }

    /**
     * Neutralizes instructor text before prompt interpolation: strips control / zero-width
     * characters, neutralizes the user-data fence delimiters, and hard-truncates at {@code maxChars}
     * (never mid surrogate pair). Preserves {@code \n}/{@code \t} for the multi-line execute-prompt body.
     */
    static String sanitizeForPrompt(@Nullable String raw, int maxChars) {
        return AtlasPromptSanitizer.sanitizeForPrompt(raw, maxChars, false, "(empty)");
    }

    static String renderCompetencyIndex(CompetencyIndexResponseDTO index) {
        List<CompetencyIndexDTO> competencies = index.competencies();
        List<CompetencyIndexResponseDTO.UnassignedExerciseRefDTO> unassigned = index.unassignedExercises();
        StringBuilder sb = new StringBuilder();
        if (competencies.isEmpty()) {
            sb.append("(no competencies defined in this course yet)\n");
        }
        else {
            for (int i = 0; i < competencies.size(); i++) {
                boolean lastCompetency = i == competencies.size() - 1;
                appendCompetencyBranch(sb, competencies.get(i), lastCompetency);
            }
        }
        sb.append('\n').append("UNASSIGNED EXERCISES (currently linked to no competency):\n");
        if (unassigned.isEmpty()) {
            sb.append("(all course exercises are linked to at least one competency)");
        }
        else {
            for (int i = 0; i < unassigned.size(); i++) {
                boolean last = i == unassigned.size() - 1;
                sb.append(last ? "└── " : "├── ").append(formatUnassignedLine(unassigned.get(i))).append('\n');
            }
        }
        return sb.toString().stripTrailing();
    }

    private static String formatUnassignedLine(CompetencyIndexResponseDTO.UnassignedExerciseRefDTO exercise) {
        String title = sanitizeForPrompt(Objects.requireNonNullElse(exercise.title(), "(untitled)"), EXERCISE_TITLE_MAX);
        String type = sanitizeForPrompt(Objects.requireNonNullElse(exercise.type(), "unknown"), TYPE_LABEL_MAX);
        return "[" + exercise.id() + "] " + title + " (" + type + ")";
    }

    private static void appendCompetencyBranch(StringBuilder sb, CompetencyIndexDTO entry, boolean lastCompetency) {
        String taxonomy = entry.taxonomy() != null ? entry.taxonomy().name() : "UNSPECIFIED";
        String safeTitle = sanitizeForPrompt(entry.title(), COMPETENCY_TITLE_MAX);
        sb.append(lastCompetency ? "└── " : "├── ").append('[').append(entry.id()).append("] ").append(safeTitle).append(" (").append(entry.type()).append(", ").append(taxonomy)
                .append(")\n");
        String childIndent = lastCompetency ? "    " : "│   ";
        boolean hasLectureUnits = !entry.lectureUnits().isEmpty();
        boolean hasRelations = !entry.relations().isEmpty();
        List<String> exerciseLines = entry.exercises().stream().map(OrchestratorPromptRenderer::formatExerciseLine).toList();
        appendLeafGroup(sb, childIndent, "exercises", exerciseLines, !hasLectureUnits && !hasRelations);
        if (hasLectureUnits) {
            List<String> lectureUnitLines = entry.lectureUnits().stream().map(OrchestratorPromptRenderer::formatLectureUnitLine).toList();
            appendLeafGroup(sb, childIndent, "lecture units", lectureUnitLines, !hasRelations);
        }
        if (hasRelations) {
            List<String> relationLines = entry.relations().stream()
                    .map(relation -> relation.tailCompetencyId() + " --" + relation.relationType() + "--> " + relation.headCompetencyId()).toList();
            appendLeafGroup(sb, childIndent, "relations", relationLines, true);
        }
    }

    private static String formatExerciseLine(CompetencyIndexDTO.ExerciseLinkRefDTO exercise) {
        String safeTitle = sanitizeForPrompt(exercise.title(), EXERCISE_TITLE_MAX);
        String safeType = sanitizeForPrompt(Objects.requireNonNullElse(exercise.type(), "unknown"), TYPE_LABEL_MAX);
        if (exercise.weight() == null) {
            return safeTitle + " (" + safeType + ")";
        }
        return safeTitle + " (" + safeType + ", w=" + String.format(Locale.ROOT, "%.1f", exercise.weight()) + ")";
    }

    private static String formatLectureUnitLine(CompetencyIndexDTO.LectureUnitRefDTO lectureUnit) {
        String safeName = sanitizeForPrompt(lectureUnit.name(), LECTURE_UNIT_NAME_MAX);
        String safeType = sanitizeForPrompt(Objects.requireNonNullElse(lectureUnit.type(), "unknown"), TYPE_LABEL_MAX);
        return safeName + " (" + safeType + ")";
    }

    private static void appendLeafGroup(StringBuilder sb, String parentIndent, String label, List<String> items, boolean lastGroup) {
        String groupBranch = lastGroup ? "└── " : "├── ";
        sb.append(parentIndent).append(groupBranch).append(label);
        if (items.isEmpty()) {
            sb.append(": —\n");
            return;
        }
        sb.append('\n');
        String leafIndent = parentIndent + (lastGroup ? "    " : "│   ");
        for (int i = 0; i < items.size(); i++) {
            boolean lastItem = i == items.size() - 1;
            sb.append(leafIndent).append(lastItem ? "└── " : "├── ").append(items.get(i)).append('\n');
        }
    }
}
