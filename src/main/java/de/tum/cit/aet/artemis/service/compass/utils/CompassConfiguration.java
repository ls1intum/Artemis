package de.tum.cit.aet.artemis.service.compass.utils;

/**
 * All similarity related parameters
 */
public class CompassConfiguration {

    /*
     * Similarity related parameters
     */
    // Weight of the relationship type when calculating the similarity of two UML relationships
    public static final double RELATION_TYPE_WEIGHT = 0.3;

    // Weight of the source and destination elements when calculating the similarity of two UML relationships (e.g. in class diagrams)
    public static final double RELATION_ELEMENT_WEIGHT = 0.25;

    // Weight of the source and destination elements when calculating the similarity of two UML component relationships
    public static final double COMPONENT_RELATION_ELEMENT_WEIGHT = 0.35;

    // Weight of the source and destination elements when calculating the similarity of two UML use case associations
    public static final double USE_CASE_ASSOCIATION_ELEMENT_WEIGHT = 0.3;

    // Weight of the multiplicity when calculating the similarity of two UML relationships
    public static final double RELATION_MULTIPLICITY_WEIGHT = 0.05;

    // Weight of the roles when calculating the similarity of two UML relationships
    public static final double RELATION_ROLE_WEIGHT = 0.05;

    // Weight of the class type when calculating the similarity of two UML classes
    public static final double CLASS_TYPE_WEIGHT = 0.3;

    // Weight of the class name when calculating the similarity of two UML classes
    public static final double CLASS_NAME_WEIGHT = 0.7;

    public static final double USE_CASE_ASSOCIATION_NAME_WEIGHT = 0.1;

    public static final double NODE_NAME_WEIGHT = 0.5;

    public static final double NODE_STEREOTYPE_WEIGHT = 0.2;

    public static final double NODE_PARENT_WEIGHT = 0.3;

    public static final double COMPONENT_NAME_WEIGHT = 0.6;

    public static final double COMPONENT_PARENT_WEIGHT = 0.4;

    // Weight of the name similarity of their attributes when calculating the similarity of two UML classes
    public static final double ATTRIBUTE_NAME_WEIGHT = 0.7;

    // Weight of the type similarity of their attributes when calculating the similarity of two UML classes
    public static final double ATTRIBUTE_TYPE_WEIGHT = 0.3;

    // Threshold used for building similarity sets. If the similarity for two UML elements is smaller than this threshold, they will not be put into the same similarity set.
    // TODO CZ: decrease equality threshold again in the future
    public static final double EQUALITY_THRESHOLD = 0.95;

    // Threshold of the confidence of an element in a UML diagram. If the confidence for a specific model element is smaller than this threshold, no automatic feedback will be
    // created for this element.
    public static final double ELEMENT_CONFIDENCE_THRESHOLD = 0.8;
}
