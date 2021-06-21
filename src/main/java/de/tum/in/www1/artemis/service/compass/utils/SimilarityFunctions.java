package de.tum.in.www1.artemis.service.compass.utils;

import java.util.Objects;

import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

public class SimilarityFunctions {

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
