package de.tum.in.www1.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.enumeration.LegalDocumentType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ImprintDTO(LegalDocumentType type, String text, Language language) implements LegalDocument {

    public ImprintDTO(String text, Language language) {
        this(LegalDocumentType.IMPRINT, text, language);
    }
}
