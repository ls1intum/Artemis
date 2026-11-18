package de.tum.cit.aet.artemis.programming.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ide.Ide;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IdeMappingDTO(ProgrammingLanguage programmingLanguage, IdeDTO ide) {

    public IdeMappingDTO(ProgrammingLanguage programmingLanguage, Ide ide) {
        this(programmingLanguage, new IdeDTO(ide));
    }
}
