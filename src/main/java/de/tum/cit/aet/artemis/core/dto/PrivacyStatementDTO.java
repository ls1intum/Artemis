package de.tum.cit.aet.artemis.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.domain.LegalDocumentType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PrivacyStatementDTO(LegalDocumentType type, String text, Language language) implements LegalDocument {

    public PrivacyStatementDTO(String text, Language language) {
        this(LegalDocumentType.PRIVACY_STATEMENT, text, language);
    }
}
