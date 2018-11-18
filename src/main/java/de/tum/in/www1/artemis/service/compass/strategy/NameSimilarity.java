package de.tum.in.www1.artemis.service.compass.strategy;

import de.tum.in.www1.artemis.service.compass.utils.CompassConfiguration;

@SuppressWarnings("unused")
public class NameSimilarity {

    /**
     *
     * @return 1 if both strings have any word in common (splitting on uppercase), 0 otherwise
     */
    public static double nameContainsSimilarity(String string1, String string2) {
        // Split before any Uppercase without excluding letters
        String[] names1 = string1.split("(?=\\p{Lu})");
        String[] names2 = string2.split("(?=\\p{Lu})");
        // Both arrays should contain less than 5 words - therefore HashSet is slower
        for (String name1: names1) {
            for (String name2: names2) {
                if (name1.equals(name2)) {
                    return 1;
                }
            }
        }
        return 0;
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
