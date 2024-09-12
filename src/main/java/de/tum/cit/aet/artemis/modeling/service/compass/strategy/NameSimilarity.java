package de.tum.cit.aet.artemis.modeling.service.compass.strategy;

import java.util.Objects;

import me.xdrop.fuzzywuzzy.FuzzySearch;

public class NameSimilarity {

    /**
     * Analyzes the similarity between two given strings by calculating a Levenshtein simple ratio.
     *
     * @param string1 the first of the two strings that should be compared
     * @param string2 the second of the two strings that should be compared
     * @return the Levenshtein simple ratio between the two input strings
     */
    public static double levenshteinSimilarity(String string1, String string2) {
        if (Objects.equals(string1, string2)) {
            return 1;
        }
        if (string1 == null || string2 == null) {
            return 0;
        }

        // TODO longterm: think about an even more sophisticated approach that takes e.g. thesaurus and specific uml conventions into account
        return FuzzySearch.ratio(string1, string2) / 100.0;
    }

    /**
     * Checks for equality of the two given strings.
     *
     * @param string1 the first of the two strings that should be compared
     * @param string2 the second of the two strings that should be compared
     * @return 1 if the strings are equal, 0 otherwise
     */
    public static double nameEqualsSimilarity(String string1, String string2) {
        return Objects.equals(string1, string2) ? 1 : 0;
    }
}
