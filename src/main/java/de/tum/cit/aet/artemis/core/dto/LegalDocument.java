package de.tum.cit.aet.artemis.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.admin.domain.LegalDocumentType;
import de.tum.cit.aet.artemis.core.domain.Language;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public interface LegalDocument {

    LegalDocumentType type();

    String text();

    Language language();
}
