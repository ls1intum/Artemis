package de.tum.in.www1.artemis.util;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ExampleSubmission;
import de.tum.in.www1.artemis.domain.enumeration.DiagramType;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;

@Service
public class ModelingExerciseUtilService {

    @Autowired
    DatabaseUtilService database;

    /**
     * Create modeling exercise for a given course
     * @param courseId  id of the given course
     * @return  created modeling exercise
     */
    public ModelingExercise createModelingExercise(Long courseId) {
        return createModelingExercise(courseId, null);
    }

    /**
     * Create modeling exercise with a given id for a given course
     * @param courseId  id of the given course
     * @param exerciseId  id of modeling exercise
     * @return  created modeling exercise
     */
    public ModelingExercise createModelingExercise(Long courseId, Long exerciseId) {
        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        ZonedDateTime futureTimestamp = ZonedDateTime.now().plusDays(5);
        ZonedDateTime futureFutureTimestamp = ZonedDateTime.now().plusDays(8);

        Course course1 = ModelFactory.generateCourse(courseId, pastTimestamp, futureTimestamp, new HashSet<>(), "tumuser", "tutor", "instructor");
        ModelingExercise modelingExercise = ModelFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.ClassDiagram, course1);
        modelingExercise.setGradingInstructions("Grading instructions");
        modelingExercise.getCategories().add("Modeling");
        modelingExercise.setId(exerciseId);
        course1.addExercises(modelingExercise);

        return modelingExercise;
    }

    /**
     * Add example submission to modeling exercise
     * @param modelingExercise  modeling exercise for which the example submission should be added
     * @return  modeling exercise with example submission
     * @throws Exception if the resources file is not found
     */
    public ModelingExercise addExampleSubmission(ModelingExercise modelingExercise) throws Exception {
        Set<ExampleSubmission> exampleSubmissionSet = new HashSet<>();
        String validModel = database.loadFileFromResources("test-data/model-submission/model.54727.json");
        var exampleSubmission = database.generateExampleSubmission(validModel, modelingExercise, true);
        exampleSubmission.assessmentExplanation("explanation");
        exampleSubmissionSet.add(exampleSubmission);
        modelingExercise.setExampleSubmissions(exampleSubmissionSet);
        return modelingExercise;
    }
}
