package de.tum.cit.aet.artemis.exercise.domain;

import java.util.EnumSet;
import java.util.Set;

/** Validated selections for participation management. Names are the public query parameter values. */
public enum ParticipationFilter {

    Failed(Group.SUBMISSION), BuildFailed(Group.SUBMISSION), NoSubmissions(Group.SUBMISSION), ValidSubmissions(Group.SUBMISSION), Successful(Group.RESULT),
    Unsuccessful(Group.RESULT), Manual(Group.ASSESSMENT), SemiAutomatic(Group.ASSESSMENT), Automatic(Group.ASSESSMENT), NoPracticeMode(Group.MODE), Graded(Group.GRADING),
    Locked(Group.LOCK);

    public enum Group {
        SUBMISSION, RESULT, ASSESSMENT, MODE, GRADING, LOCK
    }

    public final Group group;

    ParticipationFilter(Group group) {
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
            try {
                filters.add(valueOf(name.trim()));
            }
            catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Unknown participation filter: " + name.trim(), exception);
            }
        }
        return filters;
    }
}
