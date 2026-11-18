package de.tum.cit.aet.artemis.communication.domain;

import java.util.Locale;

public enum PostingType {

    POST, ANSWER;

    public static PostingType fromString(String value) {
        return PostingType.valueOf(value.toUpperCase(Locale.ROOT));
    }
}
