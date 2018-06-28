package de.tum.in.www1.artemis.service.compass.grade;

import java.util.Map;

public interface Grade {

    double getPoints();

    double getConfidence();

    double getCoverage();

    Map<String, String> getJsonIdCommentsMapping();

    Map<String, Double> getJsonIdPointsMapping();

}
