package de.tum.cit.aet.artemis.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.admin.domain.LegalDocumentType;
import de.tum.cit.aet.artemis.core.domain.Language;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ImprintDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) LegalDocumentType type,
        @Schema(description = "Markdown content, omitted when empty", requiredMode = Schema.RequiredMode.NOT_REQUIRED) String text,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Language language) implements LegalDocument {

    public ImprintDTO(String text, Language language) {
        this(LegalDocumentType.IMPRINT, text, language);
    }
}
