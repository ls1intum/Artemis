package de.tum.cit.aet.artemis.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.admin.domain.LegalDocumentType;
import de.tum.cit.aet.artemis.core.domain.Language;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ImprintDTO(LegalDocumentType type, String text, Language language) implements LegalDocument {

    public ImprintDTO(String text, Language language) {
        this(LegalDocumentType.IMPRINT, text, language);
    }
}
