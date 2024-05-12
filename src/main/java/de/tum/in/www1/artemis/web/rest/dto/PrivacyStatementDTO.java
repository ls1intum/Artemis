package de.tum.in.www1.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.enumeration.LegalDocumentType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PrivacyStatementDTO(LegalDocumentType type, String text, Language language) implements LegalDocument {

    public PrivacyStatementDTO(String text, Language language) {
        this(LegalDocumentType.PRIVACY_STATEMENT, text, language);
    }
}
