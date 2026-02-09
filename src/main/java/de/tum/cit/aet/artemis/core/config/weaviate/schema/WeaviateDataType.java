package de.tum.cit.aet.artemis.core.config.weaviate.schema;

/**
 * Weaviate data types for property definitions.
 * These correspond to the <a href="https://docs.weaviate.io/weaviate/config-refs/datatypes">data types supported by Weaviate</a>.
 */
public enum WeaviateDataType {

    /**
     * Integer type for whole numbers.
     */
    INT("int"),

    /**
     * Text type for string values that can be tokenized for search.
     */
    TEXT("text"),

    /**
     * Number type for floating-point values.
     */
    NUMBER("number"),

    /**
     * Boolean type for true/false values.
     */
    BOOLEAN("boolean"),

    /**
     * Date type for date/time values.
     */
    DATE("date"),

    /**
     * UUID type for unique identifiers.
     */
    UUID("uuid"),

    /**
     * Blob type for binary data.
     */
    BLOB("blob");

    private final String weaviateName;

    WeaviateDataType(String weaviateName) {
        this.weaviateName = weaviateName;
    }

    /**
     * Gets the Weaviate-compatible name for this data type.
     *
     * @return the Weaviate data type name
     */
    public String getWeaviateName() {
        return weaviateName;
    }

    /**
     * Parses a Weaviate data type name into the enum.
     *
     * @param name the Weaviate data type name
     * @return the corresponding enum value
     * @throws IllegalArgumentException if the name is not recognized
     */
    public static WeaviateDataType fromWeaviateName(String name) {
        for (WeaviateDataType type : values()) {
            if (type.weaviateName.equalsIgnoreCase(name)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown Weaviate data type: " + name);
    }
}
