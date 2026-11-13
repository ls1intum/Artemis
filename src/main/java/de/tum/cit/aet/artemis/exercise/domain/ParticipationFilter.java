package de.tum.cit.aet.artemis.exercise.domain;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/** Validated selections for participation management. Names are the public query parameter values. */
public enum ParticipationFilter {

    FAILED("Failed", Group.SUBMISSION), BUILD_FAILED("BuildFailed", Group.SUBMISSION), NO_SUBMISSIONS("NoSubmissions", Group.SUBMISSION),
    VALID_SUBMISSIONS("ValidSubmissions", Group.SUBMISSION), SUCCESSFUL("Successful", Group.RESULT), UNSUCCESSFUL("Unsuccessful", Group.RESULT), MANUAL("Manual", Group.ASSESSMENT),
    SEMI_AUTOMATIC("SemiAutomatic", Group.ASSESSMENT), AUTOMATIC("Automatic", Group.ASSESSMENT), NO_PRACTICE_MODE("NoPracticeMode", Group.MODE), GRADED("Graded", Group.GRADING),
    LOCKED("Locked", Group.LOCK);

    public enum Group {
        SUBMISSION, RESULT, ASSESSMENT, MODE, GRADING, LOCK
    }

    private final String apiValue;

    public final Group group;

    ParticipationFilter(String apiValue, Group group) {
        this.apiValue = apiValue;
        this.group = group;
    }

    /**
     * Parses query selections, accepting legacy unfiltered values and rejecting unknown names.
     *
     * @param value comma-separated selections, or null for no filters
     * @return trimmed, de-duplicated filter selections
     */
    public static Set<ParticipationFilter> parse(String value) {
        var filters = EnumSet.noneOf(ParticipationFilter.class);
        if (value == null || value.isBlank() || value.equals("All") || value.equals("ALL")) {
            return filters;
        }
        for (String name : value.split(",", -1)) {
            String selection = name.trim();
            filters.add(Arrays.stream(values()).filter(filter -> filter.apiValue.equals(selection)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown participation filter: " + selection)));
        }
        return filters;
    }
}
