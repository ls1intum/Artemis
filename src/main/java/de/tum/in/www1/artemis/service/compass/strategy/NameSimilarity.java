package de.tum.in.www1.artemis.service.compass.strategy;

import me.xdrop.fuzzywuzzy.FuzzySearch;
import de.tum.in.www1.artemis.service.compass.utils.CompassConfiguration;

@SuppressWarnings("unused")
public class NameSimilarity {

    /**
     * Analyzes the similarity between two given strings by calculating a Levenshtein simple ratio.
     *
     * @param string1 the first of the two strings that should be compared
     * @param string2 the second of the two strings that should be compared
     * @return the Levenshtein simple ratio between the two input strings
     */
    public static double levenshteinSimilarity(String string1, String string2) {
        // TODO longterm: think about an even more sophisticated approach that takes e.g. thesaurus and specific uml conventions into account
        return FuzzySearch.ratio(string1, string2) / 100.0;
    }

    public static double nameEqualsSimilarity(String string1, String string2) {
        return string1.equals(string2) ? 1 : 0;
    }

    // TODO CZ: this is not a good similarity calculation
    public static double namePartiallyEqualsSimilarity(String string1, String string2) {
        if (string1.equals(string2)) {
            return 1;
        }
        else if ((string1.length() > 3 && string2.length() > 3) && (string1.substring(0, 3).equals(string2.substring(0, 3)))) {
            return CompassConfiguration.PARTIALLY_NAME_WEIGHT;
        }
        return 0;
    }
}
