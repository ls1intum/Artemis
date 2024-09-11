package de.tum.cit.aet.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.domain.LegalDocumentType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ImprintDTO(LegalDocumentType type, String text, Language language) implements LegalDocument {

    public ImprintDTO(String text, Language language) {
        this(LegalDocumentType.IMPRINT, text, language);
    }
}
