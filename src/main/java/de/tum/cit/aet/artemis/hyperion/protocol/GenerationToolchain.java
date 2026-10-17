package de.tum.cit.aet.artemis.hyperion.protocol;

import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Identifies a qualified language/build-system adapter. Image identity is checked separately for every execution. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GenerationToolchain(String id) {

    private static final Pattern ID_PATTERN = Pattern.compile("[a-z][a-z0-9]*(?:-[a-z0-9]+)*");

    public static final GenerationToolchain JAVA_GRADLE = new GenerationToolchain("java-gradle");

    public GenerationToolchain {
        if (id == null || id.length() > 64 || !ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("A bounded language/toolchain identifier is required");
        }
    }
}
