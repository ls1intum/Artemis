package de.tum.cit.aet.artemis.atlas.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_ATLAS;

import org.springframework.core.env.Environment;

import de.tum.cit.aet.artemis.core.api.AbstractApi;

public abstract class AbstractAtlasApi extends AbstractApi {

    protected AbstractAtlasApi(Environment environment) {
        super(environment, PROFILE_ATLAS);
    }
}
