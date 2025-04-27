package de.tum.cit.aet.artemis.programming.domain;

import jakarta.persistence.AttributeConverter;

import org.springframework.beans.factory.annotation.Value;

public class RepositoryUrlConverter implements AttributeConverter<String, String> {

    private final String baseUrl;

    private final String postfix = ".git";

    public RepositoryUrlConverter(@Value("${artemis.version-control.url}") String baseUrl) {
        this.baseUrl = baseUrl + "/git/";
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }

        // Remove base URL if present
        if (attribute.startsWith(baseUrl)) {
            attribute = attribute.substring(baseUrl.length());
        }

        if (attribute.endsWith(postfix)) {
            attribute = attribute.substring(0, attribute.length() - postfix.length());
        }
        return attribute;
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        // Add base URL back
        return baseUrl + dbData + postfix;
    }
}
