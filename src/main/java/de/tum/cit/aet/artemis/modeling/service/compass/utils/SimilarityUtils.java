package de.tum.cit.aet.artemis.modeling.service.compass.utils;

import java.util.Objects;

import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.UMLElement;

public class SimilarityUtils {

    /**
     * Checks if both parent elements have similarity id
     * If they have returns whether they are equal or not
     * If not returns whether they are similar
     *
     * @param parent1 the first parent element
     * @param parent2 the second parent element
     * @return whether the elements equal or similar depending on their similarity id
     */
    public static boolean parentsSimilarOrEqual(UMLElement parent1, UMLElement parent2) {
        if (parent1 == null) {
            return parent2 == null;
        }
        else if (parent2 == null) {
            return false;
        }
        boolean similarButNotCalculated = (parent1.getSimilarityID() == -1 || parent2.getSimilarityID() == -1)
                && parent1.similarity(parent2) > CompassConfiguration.EQUALITY_THRESHOLD;
        if (similarButNotCalculated) {
            return true;
        }
        return Objects.equals(parent1, parent2);
    }
}
