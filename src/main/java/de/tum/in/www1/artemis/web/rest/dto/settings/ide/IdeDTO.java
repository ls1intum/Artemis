package de.tum.in.www1.artemis.web.rest.dto.settings.ide;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.settings.ide.Ide;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IdeDTO(String name, String deepLink) {

    public IdeDTO(Ide ide) {
        this(ide.getName(), ide.getDeepLink());
    }
}
