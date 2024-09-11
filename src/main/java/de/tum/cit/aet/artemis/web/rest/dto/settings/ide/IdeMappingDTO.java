package de.tum.cit.aet.artemis.web.rest.dto.settings.ide;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.cit.aet.artemis.domain.settings.ide.Ide;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IdeMappingDTO(ProgrammingLanguage programmingLanguage, IdeDTO ide) {

    public IdeMappingDTO(ProgrammingLanguage programmingLanguage, Ide ide) {
        this(programmingLanguage, new IdeDTO(ide));
    }
}
