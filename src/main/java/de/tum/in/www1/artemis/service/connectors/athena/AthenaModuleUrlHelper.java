package de.tum.in.www1.artemis.service.connectors.athena;

import org.springframework.beans.factory.annotation.Value;

import de.tum.in.www1.artemis.domain.enumeration.ExerciseType;

/**
 * Service to get the URL for an Athena module, depending on the type of exercise.
 */
public class AthenaModuleUrlHelper {

    @Value("${artemis.athena.url}")
    private static String athenaUrl;

    @Value("${artemis.athena.modules.text}")
    private static String textModuleName;

    @Value("${artemis.athena.modules.programming}")
    private static String programmingModuleName;

    /**
     * Get the URL for an Athena module, depending on the type of exercise.
     *
     * @param exerciseType The type of exercise
     * @return The URL prefix to access the Athena module. Example: "http://athena.example.com/modules/text/module_text_cofee"
     */
    public static String getAthenaModuleUrl(ExerciseType exerciseType) {
        switch (exerciseType) {
            case TEXT -> {
                return athenaUrl + "/modules/text/" + textModuleName;
            }
            case PROGRAMMING -> {
                return athenaUrl + "/modules/programming/" + programmingModuleName;
            }
            default -> throw new IllegalArgumentException("Exercise type not supported: " + exerciseType);
        }
    }
}
