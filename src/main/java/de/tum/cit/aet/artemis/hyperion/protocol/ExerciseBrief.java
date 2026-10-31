package de.tum.cit.aet.artemis.hyperion.protocol;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Authoring facts only: no course membership, repository URI or mutable persistence entity. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseBrief(@JsonInclude String title, @JsonInclude String shortName, @JsonInclude String namespace, @JsonInclude @Nullable String problemStatement,
        @JsonInclude String prompt, Mode mode, @JsonInclude @Nullable String sourceBrief, GenerationToolchain toolchain, @Nullable GenerationBuildTool buildTool) {

    /**
     * Gradle is the absent default: a Gradle brief serializes exactly as before Maven support, so a payload from or for an older peer keeps its meaning.
     */
    public ExerciseBrief {
        if (buildTool == GenerationBuildTool.GRADLE) {
            buildTool = null;
        }
        if (title == null || title.length() > 255 || shortName == null || shortName.length() > 255 || namespace == null || namespace.length() > 255 || prompt == null
                || prompt.length() > 65_536 || mode == null || toolchain == null || (problemStatement != null && problemStatement.length() > 65_536)
                || (sourceBrief != null && sourceBrief.length() > 65_536)) {
            throw new IllegalArgumentException("Invalid exercise authoring brief");
        }
    }

    public ExerciseBrief(String title, String shortName, String namespace, @Nullable String problemStatement, String prompt, Mode mode) {
        this(title, shortName, namespace, problemStatement, prompt, mode, null, GenerationToolchain.JAVA_GRADLE, null);
    }

    public ExerciseBrief(String title, String shortName, String namespace, @Nullable String problemStatement, String prompt, Mode mode, GenerationToolchain toolchain) {
        this(title, shortName, namespace, problemStatement, prompt, mode, null, toolchain, null);
    }

    public ExerciseBrief(String title, String shortName, String namespace, @Nullable String problemStatement, String prompt, Mode mode, @Nullable String sourceBrief) {
        this(title, shortName, namespace, problemStatement, prompt, mode, sourceBrief, GenerationToolchain.JAVA_GRADLE, null);
    }

    /**
     * Resolves the absent default.
     *
     * @return the build tool whose canonical phases verify this exercise
     */
    public GenerationBuildTool resolvedBuildTool() {
        return buildTool == null ? GenerationBuildTool.GRADLE : buildTool;
    }

    /** Existing production generation intent, independent of worker placement. */
    public enum Mode {
        GENERATE, ADAPT
    }
}
