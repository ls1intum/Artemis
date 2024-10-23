package de.tum.cit.aet.artemis.exercise.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.api.AbstractApi;

@Controller
public class AbstractExerciseApi extends AbstractApi {

    public AbstractExerciseApi(Environment environment) {
        super(environment, PROFILE_CORE);
    }
}
