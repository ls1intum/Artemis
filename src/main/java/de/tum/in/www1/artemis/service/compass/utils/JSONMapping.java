package de.tum.in.www1.artemis.service.compass.utils;

/**
 * JSON keywords
 */
public class JSONMapping {
    // general
    public static final String elementID = "id";

    // model base structure
    public static final String elements = "elements";
    public static final String relationships = "relationships";

    // elements
    public static final String elementOwner = "owner";
    public static final String elementName = "name";
    public static final String elementAttributes = "attributes";
    public static final String elementMethods = "methods";
    public static final String elementType = "type";

    // relationships
    public static final String relationshipSource = "source";
    public static final String relationshipTarget = "target";
    public static final String relationshipEndpointID = "element";
    public static final String relationshipType = "type";
    public static final String relationshipMultiplicity = "multiplicity";
    public static final String relationshipRole= "role";

    // assessments
    public static final String assessments = "assessments";
    public static final String assessmentPoints = "credits";
    public static final String assessmentComment = "comment";

    public static final String assessmentElementID = elementID;

    public static final String assessmentElementCoverage = "coverage";
    public static final String assessmentElementConfidence = "confidence";
}
