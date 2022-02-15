package de.tum.in.www1.artemis.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

@Component
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "info.guided-tour")
public class GuidedTourConfiguration {

    private String courseShortName = "";

    private List<Map<String, String>> tours = new ArrayList<>();

    /**
     * Get the list of the mapping tourKey -> exerciseIdentifier from the info.guided-tour configuration in the application.yml file
     * @return List of mappings of tourKey -> exerciseIdentifier
     */
    public List<Map<String, String>> getTours() {
        return this.tours;
    }

    public void setTours(List<Map<String, String>> tours) {
        this.tours = tours;
    }

    public String getCourseShortName() {
        return courseShortName;
    }

    public void setCourseShortName(String courseShortName) {
        this.courseShortName = courseShortName;
    }

    /**
     * Helper method to determine if the given exercise mapped to a guided tour
     * based on the info.guided-tour configuration in the application.yml file
     * Throws an exception if the exercise is not part of a tutorial
     * @param exercise  the exercise for which the configuration has to be determined
     */
    public void checkExerciseForTutorialElseThrow(Exercise exercise) {
        String exerciseId = exercise instanceof ProgrammingExercise ? exercise.getShortName() : exercise.getTitle();
        tours.stream().flatMap(tour -> tour.values().stream()).filter(tourId -> tourId.equals(exerciseId)).findAny()
                .orElseThrow(() -> new AccessForbiddenException("Not allowed! This exercise is not part of a tutorial"));
    }
}
