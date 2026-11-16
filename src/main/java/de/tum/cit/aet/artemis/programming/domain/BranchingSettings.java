package de.tum.cit.aet.artemis.programming.domain;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Whether students may push to branches other than the default branch of their repository and which branch names are allowed.
 *
 * @param allowed whether branching is allowed
 * @param regex   the regex a branch name has to match, {@code null} means {@link #DEFAULT_REGEX}. Only considered if branching is allowed.
 *                    An empty regex is kept as it is (it matches no branch name). The record therefore has no {@code @JsonInclude(NON_EMPTY)},
 *                    which would leave it out of the stored JSON, and a {@code null} regex is stored as JSON null.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BranchingSettings(boolean allowed, @Nullable String regex) {

    public static final String DEFAULT_REGEX = ".*";

    /**
     * Whether these settings equal the behaviour of an exercise that never configured branching.
     *
     * @return true if branching is not allowed and the regex is the default one
     */
    @JsonIgnore
    public boolean isDefault() {
        return !allowed && (regex == null || DEFAULT_REGEX.equals(regex));
    }
}
