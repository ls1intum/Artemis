package de.tum.cit.aet.artemis.communication.domain;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum SourceType {

    POST("post"), ANSWER_POST("answer_post");

    private final String value;

    SourceType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @JsonCreator
    public static SourceType fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("SourceType value cannot be null");
        }
        for (SourceType type : SourceType.values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid SourceType value: " + value);
    }
}
