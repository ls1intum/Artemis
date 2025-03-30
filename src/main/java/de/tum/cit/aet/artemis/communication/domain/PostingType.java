package de.tum.cit.aet.artemis.communication.domain;

public enum PostingType {

    POST, ANSWER;

    public static PostingType fromString(String value) {
        return PostingType.valueOf(value.toUpperCase());
    }
}
