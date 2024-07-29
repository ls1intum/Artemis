package de.tum.in.www1.artemis.web.rest.settings.ide;

import de.tum.in.www1.artemis.domain.settings.ide.Ide;

public record IdeDTO(String name, String deepLink) {

    public IdeDTO(Ide ide) {
        this(ide.getName(), ide.getDeepLink());
    }
}
