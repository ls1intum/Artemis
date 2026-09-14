package de.tum.cit.aet.artemis.hyperion.protocol;

import javax.lang.model.SourceVersion;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Authoring facts only: no course membership, repository URI or mutable persistence entity. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseBrief(@JsonInclude String title, @JsonInclude String shortName, String packageName, @JsonInclude @Nullable String problemStatement,
        @JsonInclude String prompt, Mode mode) {

    public ExerciseBrief {
        if (title == null || title.length() > 255 || shortName == null || shortName.length() > 255 || packageName == null || packageName.length() > 255 || prompt == null
                || prompt.length() > 65_536 || mode == null) {
            throw new IllegalArgumentException("Invalid exercise authoring brief");
        }
        if (!SourceVersion.isName(packageName)) {
            throw new IllegalArgumentException("Invalid Java package name");
        }
    }

    /** Existing production generation intent, independent of worker placement. */
    public enum Mode {
        GENERATE, ADAPT
    }
}
