package de.tum.cit.aet.artemis.hyperion.protocol;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Authoring facts only: no course membership, repository URI or mutable persistence entity. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseBrief(String title, String shortName, String packageName, @Nullable String problemStatement, String prompt, Mode mode, @Nullable String sourceBrief) {

    public ExerciseBrief {
        if (title == null || title.length() > 255 || shortName == null || shortName.length() > 255 || packageName == null || packageName.length() > 255 || prompt == null
                || prompt.length() > 65_536 || mode == null || (problemStatement != null && problemStatement.length() > 65_536)
                || (sourceBrief != null && sourceBrief.length() > 65_536)) {
            throw new IllegalArgumentException("Invalid exercise authoring brief");
        }
        if (!packageName.matches("[a-zA-Z_$][a-zA-Z0-9_$]*(?:\\.[a-zA-Z_$][a-zA-Z0-9_$]*)*")) {
            throw new IllegalArgumentException("Invalid Java package name");
        }
    }

    public ExerciseBrief(String title, String shortName, String packageName, @Nullable String problemStatement, String prompt, Mode mode) {
        this(title, shortName, packageName, problemStatement, prompt, mode, null);
    }

    /** Existing production generation intent, independent of worker placement. */
    public enum Mode {
        GENERATE, ADAPT
    }
}
