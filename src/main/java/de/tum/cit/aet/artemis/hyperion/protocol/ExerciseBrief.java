package de.tum.cit.aet.artemis.hyperion.protocol;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Authoring facts only: no course membership, repository URI or mutable persistence entity. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseBrief(@JsonInclude String title, @JsonInclude String shortName, @JsonInclude String namespace, @JsonInclude @Nullable String problemStatement,
        @JsonInclude String prompt, Mode mode, @JsonInclude @Nullable String sourceBrief, GenerationToolchain toolchain) {

    public ExerciseBrief {
        if (title == null || title.length() > 255 || shortName == null || shortName.length() > 255 || namespace == null || namespace.length() > 255 || prompt == null
                || prompt.length() > 65_536 || mode == null || toolchain == null || (problemStatement != null && problemStatement.length() > 65_536)
                || (sourceBrief != null && sourceBrief.length() > 65_536)) {
            throw new IllegalArgumentException("Invalid exercise authoring brief");
        }
    }

    public ExerciseBrief(String title, String shortName, String namespace, @Nullable String problemStatement, String prompt, Mode mode) {
        this(title, shortName, namespace, problemStatement, prompt, mode, null, GenerationToolchain.JAVA_GRADLE);
    }

    public ExerciseBrief(String title, String shortName, String namespace, @Nullable String problemStatement, String prompt, Mode mode, GenerationToolchain toolchain) {
        this(title, shortName, namespace, problemStatement, prompt, mode, null, toolchain);
    }

    public ExerciseBrief(String title, String shortName, String namespace, @Nullable String problemStatement, String prompt, Mode mode, @Nullable String sourceBrief) {
        this(title, shortName, namespace, problemStatement, prompt, mode, sourceBrief, GenerationToolchain.JAVA_GRADLE);
    }

    /** Existing production generation intent, independent of worker placement. */
    public enum Mode {
        GENERATE, ADAPT
    }
}
