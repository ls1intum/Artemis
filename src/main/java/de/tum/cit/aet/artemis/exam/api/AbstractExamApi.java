package de.tum.cit.aet.artemis.exam.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_EXAM;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.api.AbstractApi;

@Controller
abstract class AbstractExamApi extends AbstractApi {

    public AbstractExamApi(Environment environment) {
        super(environment, PROFILE_EXAM);
    }
}
