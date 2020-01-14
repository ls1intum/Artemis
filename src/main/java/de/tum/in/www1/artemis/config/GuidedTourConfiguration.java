package de.tum.in.www1.artemis.config;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "info.guided-tour")
public class GuidedTourConfiguration {

    private final Logger log = LoggerFactory.getLogger(GuidedTourConfiguration.class);

    private String courseShortName = "";

    private List<HashMap<String, String>> tours = new ArrayList<>();

    public GuidedTourConfiguration() {

    }

    public List<HashMap<String, String>> getTours() { return this.tours; }

    public void setTours(List<HashMap<String, String>> tours) {
        this.tours = tours;
    }

    public String getCourseShortName() {
        return courseShortName;
    }

    public void setCourseShortName(String courseShortName) {
        this.courseShortName = courseShortName;
    }

    public boolean isExerciseForTutorial(Exercise exercise) {
        String exerciseIdentifier = exercise instanceof ProgrammingExercise ? exercise.getShortName() : exercise.getTitle();

        for (HashMap<String, String> guidedTourMapping : this.getTours()) {
            if (guidedTourMapping.values().iterator().next().equals(exerciseIdentifier)) {
                return true;
            }
        }
        return false;
    }
}
