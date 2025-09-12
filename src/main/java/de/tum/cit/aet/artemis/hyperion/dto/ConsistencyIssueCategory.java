package de.tum.cit.aet.artemis.hyperion.dto;

/**
 * Unified category enum for consistency issues. Composed of structural and semantic categories.
 */
public enum ConsistencyIssueCategory {

    // Structural
    METHOD_RETURN_TYPE_MISMATCH, METHOD_PARAMETER_MISMATCH, CONSTRUCTOR_PARAMETER_MISMATCH, ATTRIBUTE_TYPE_MISMATCH, VISIBILITY_MISMATCH,

    // Semantic
    IDENTIFIER_NAMING_INCONSISTENCY;
}
