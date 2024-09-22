package de.tum.cit.aet.artemis.athena.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_ATHENA;

import org.springframework.core.env.Environment;

import de.tum.cit.aet.artemis.core.api.AbstractApi;

abstract class AbstractAthenaApi extends AbstractApi {

    public AbstractAthenaApi(Environment environment) {
        super(environment, PROFILE_ATHENA);
    }
}
