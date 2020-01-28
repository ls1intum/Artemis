package de.tum.in.www1.artemis.config;

import java.util.*;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;

@Component
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "info.guided-tour")
public class GuidedTourConfiguration {

    private String courseShortName = "";

    private List<Map<String, String>> tours = new ArrayList<>();

    public GuidedTourConfiguration() {

    }

    /**
     * Get the list of of the mapping tourKey -> exerciseIdentifier from the info.guided-tour configuration in the application.yml file
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
     * @param exercise  the exercise for which the configuration has to be determined
     * @return  true if the exercise is used for a guided tour
     */
    public boolean isExerciseForTutorial(Exercise exercise) {
        String exerciseIdentifier = exercise instanceof ProgrammingExercise ? exercise.getShortName() : exercise.getTitle();
        return tours.stream().flatMap(tour -> tour.values().stream()).anyMatch(tourIdentifier -> tourIdentifier.equals(exerciseIdentifier));
    }
}
