package de.tum.cit.aet.artemis.hyperion.protocol;

import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Authoring facts only: no course membership, repository URI or mutable persistence entity. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseBrief(String title, String shortName, String packageName, @Nullable String problemStatement, String prompt, Mode mode) {

    private static final Pattern JAVA_PACKAGE_NAME = Pattern.compile("[a-zA-Z_$][a-zA-Z0-9_$]*(?:\\.[a-zA-Z_$][a-zA-Z0-9_$]*)*");

    public ExerciseBrief {
        if (title == null || title.length() > 255 || shortName == null || shortName.length() > 255 || packageName == null || packageName.length() > 255 || prompt == null
                || prompt.length() > 65_536 || mode == null) {
            throw new IllegalArgumentException("Invalid exercise authoring brief");
        }
        if (!JAVA_PACKAGE_NAME.matcher(packageName).matches()) {
            throw new IllegalArgumentException("Invalid Java package name");
        }
    }

    /** Existing production generation intent, independent of worker placement. */
    public enum Mode {
        GENERATE, ADAPT
    }
}
