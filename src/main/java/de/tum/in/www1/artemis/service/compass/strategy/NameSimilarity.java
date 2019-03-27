package de.tum.in.www1.artemis.service.compass.strategy;

import de.tum.in.www1.artemis.service.compass.utils.CompassConfiguration;
import me.xdrop.fuzzywuzzy.FuzzySearch;

@SuppressWarnings("unused")
public class NameSimilarity {

    /**
     * @return 1 if both strings have any word in common (splitting on uppercase), 0 otherwise
     */
    public static double nameContainsSimilarity(String string1, String string2) {
        //TODO longterm: think about an even more sophisticated approach that takes e.g. thesaurus and specific uml conventions into account
        return FuzzySearch.ratio(string1, string2) / 100.0;
     }

    public static double nameEqualsSimilarity(String string1, String string2) {
        return string1.equals(string2) ? 1 : 0;
    }

    public static double namePartiallyEqualsSimilarity(String string1, String string2) {
        if (string1.equals(string2)) {
            return 1;
        } else if ((string1.length() > 3 && string2.length() > 3) && (string1.substring(0, 3).equals(string2.substring(0, 3)))) {
            return CompassConfiguration.PARTIALLY_NAME_WEIGHT;
        }
        return 0;
    }
}
