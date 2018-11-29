package de.tum.in.www1.artemis.service.compass.utils;

/**
 * All similarity related parameters
 */
public class CompassConfiguration {

    public final static double MISSING_ELEMENT_PENALTY = 0.05;

    public final static double RELATION_TYPE_WEIGHT = 0.3;
    public final static double RELATION_ELEMENT_WEIGHT = 0.35;
    public final static double RELATION_MULTIPLICITY_OPTIONAL_WEIGHT = 0.05;
    public final static double RELATION_ROLE_OPTIONAL_WEIGHT = 0.05;

    public final static double CLASS_TYPE_WEIGHT = 0.3;
    public final static double CLASS_NAME_WEIGHT = 0.7;

    public final static double ATTRIBUTE_NAME_WEIGHT = 0.7;
    public final static double ATTRIBUTE_TYPE_WEIGHT = 0.3;

    public final static double NO_MATCH_THRESHOLD = 0.1;
    public final static double EQUALITY_THRESHOLD = 0.83;
    public final static double POORLY_ASSESSED_MODEL_THRESHOLD = 0.8;

    public final static double PARTIALLY_NAME_WEIGHT = 0.8;

}
