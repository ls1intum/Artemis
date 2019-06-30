package de.tum.in.www1.artemis.service.compass.utils;

/**
 * All similarity related parameters
 */
public class CompassConfiguration {

    // Similarity related parameters
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

    // TODO CZ: decrease equality threshold again in the future
    public final static double EQUALITY_THRESHOLD = 0.95;

    public final static double POORLY_ASSESSED_MODEL_THRESHOLD = 0.8;

    public final static double PARTIALLY_NAME_WEIGHT = 0.8;

    // Confidence and coverage parameters
    public static final double COVERAGE_THRESHOLD = 0.8;

    public static final double CONFIDENCE_THRESHOLD = 0.75;

    public static final double ELEMENT_CONFIDENCE_THRESHOLD = 0.8;

    // Calculation engine cleanup parameters
    public static final int DAYS_TO_KEEP_UNUSED_ENGINE = 1;

    public static final int TIME_TO_CHECK_FOR_UNUSED_ENGINES = 3600000;

    // Number of optimal models to keep in cache
    public static final int NUMBER_OF_OPTIMAL_MODELS = 10;
}
