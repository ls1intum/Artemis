package de.tum.cit.aet.artemis.athena.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.athena.config.AthenaEnabled;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/**
 * Service to get the URL for an Athena module, depending on the type of exercise.
 * Module names are configured via application properties (artemis.athena.modules.*).
 */
@Lazy
@Service
@Conditional(AthenaEnabled.class)
public class AthenaModuleService {

    @Value("${artemis.athena.url}")
    private String athenaUrl;

    @Value("${artemis.athena.modules.text}")
    private String textModule;

    @Value("${artemis.athena.modules.programming}")
    private String programmingModule;

    @Value("${artemis.athena.modules.modeling}")
    private String modelingModule;

    private static final Logger log = LoggerFactory.getLogger(AthenaModuleService.class);

    /**
     * Get the URL for the Athena module for the given exercise type.
     * The module name is determined by application configuration (not per-exercise).
     *
     * @param exercise The exercise for which the Athena module URL should be returned
     * @return The URL prefix to access the Athena module
     */
    public String getAthenaModuleUrl(Exercise exercise) {
        return switch (exercise.getExerciseType()) {
            case TEXT -> athenaUrl + "/modules/text/" + textModule;
            case PROGRAMMING -> athenaUrl + "/modules/programming/" + programmingModule;
            case MODELING -> athenaUrl + "/modules/modeling/" + modelingModule;
            default -> throw new IllegalArgumentException("Exercise type not supported: " + exercise.getExerciseType());
        };
    }
}
