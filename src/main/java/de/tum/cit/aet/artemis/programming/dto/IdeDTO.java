package de.tum.cit.aet.artemis.programming.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.ide.Ide;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IdeDTO(String name, String deepLink) {

    public IdeDTO(Ide ide) {
        this(ide.getName(), ide.getDeepLink());
    }
}
