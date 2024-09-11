package de.tum.cit.aet.artemis.service.compass.utils;

/**
 * JSON keywords
 */
public class JSONMapping {

    // general
    public static final String ELEMENT_ID = "id";

    // model base structure
    public static final String DIAGRAM_TYPE = "type";

    // model schema version
    public static final String DIAGRAM_VERSION = "version";

    public static final String ELEMENTS = "elements";

    public static final String RELATIONSHIPS = "relationships";

    // elements
    public static final String ELEMENT_OWNER = "owner";

    public static final String ELEMENT_NAME = "name";

    public static final String STEREOTYPE_NAME = "stereotype";

    public static final String ELEMENT_ATTRIBUTES = "attributes";

    public static final String ELEMENT_METHODS = "methods";

    public static final String ELEMENT_TYPE = "type";

    // relationships
    public static final String RELATIONSHIP_SOURCE = "source";

    public static final String RELATIONSHIP_TARGET = "target";

    public static final String RELATIONSHIP_ENDPOINT_ID = "element";

    public static final String RELATIONSHIP_MESSAGES = "messages";

    public static final String RELATIONSHIP_TYPE = "type";

    public static final String RELATIONSHIP_MULTIPLICITY = "multiplicity";

    public static final String RELATIONSHIP_ROLE = "role";

    // assessments
    public static final String ASSESSMENTS = "assessments";

    public static final String ASSESSMENT_POINTS = "credits";

    public static final String ASSESSMENT_COMMENT = "comment";

    public static final String ASSESSMENT_ELEMENT_ID = ELEMENT_ID;

    public static final String ASSESSMENT_ELEMENT_COVERAGE = "coverage";

    public static final String ASSESSMENT_ELEMENT_CONFIDENCE = "confidence";
}
