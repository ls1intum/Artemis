package de.tum.in.www1.artemis.exercise.modelingexercise;

import java.time.ZonedDateTime;
import java.util.HashSet;

import de.tum.in.www1.artemis.course.CourseFactory;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.enumeration.DiagramType;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.modeling.ApollonDiagram;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.exercise.ExerciseFactory;

/**
 * Factory for creating ModelingExercises and related objects.
 */
public class ModelingExerciseFactory {

    public static ModelingExercise generateModelingExercise(ZonedDateTime releaseDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, DiagramType diagramType,
            Course course) {
        var modelingExercise = (ModelingExercise) ExerciseFactory.populateExercise(new ModelingExercise(), releaseDate, dueDate, assessmentDueDate, course);
        modelingExercise.setDiagramType(diagramType);
        modelingExercise.setExampleSolutionModel("This is my example solution model");
        modelingExercise.setExampleSolutionExplanation("This is my example solution model");
        return modelingExercise;
    }

    public static ModelingExercise generateModelingExerciseForExam(DiagramType diagramType, ExerciseGroup exerciseGroup) {
        var modelingExercise = (ModelingExercise) ExerciseFactory.populateExerciseForExam(new ModelingExercise(), exerciseGroup);
        modelingExercise.setDiagramType(diagramType);
        modelingExercise.setExampleSolutionModel("This is my example solution model");
        modelingExercise.setExampleSolutionExplanation("This is my example solution model");
        return modelingExercise;
    }

    public static ModelingExercise generateModelingExerciseForExam(DiagramType diagramType, ExerciseGroup exerciseGroup, String title) {
        var modelingExercise = (ModelingExercise) ExerciseFactory.populateExerciseForExam(new ModelingExercise(), exerciseGroup, title);
        modelingExercise.setDiagramType(diagramType);
        modelingExercise.setExampleSolutionModel("This is my example solution model");
        modelingExercise.setExampleSolutionExplanation("This is my example solution model");
        return modelingExercise;
    }

    public static ApollonDiagram generateApollonDiagram(DiagramType diagramType, String title) {
        ApollonDiagram apollonDiagram = new ApollonDiagram();
        apollonDiagram.setDiagramType(diagramType);
        apollonDiagram.setTitle(title);
        return apollonDiagram;
    }

    /**
     * Create modeling exercise for a given course
     *
     * @param courseId id of the given course
     * @return created modeling exercise
     */
    public static ModelingExercise createModelingExercise(Long courseId) {
        return createModelingExercise(courseId, null);
    }

    /**
     * Create modeling exercise with a given id for a given course
     *
     * @param courseId   id of the given course
     * @param exerciseId id of modeling exercise
     * @return created modeling exercise
     */
    public static ModelingExercise createModelingExercise(Long courseId, Long exerciseId) {
        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        ZonedDateTime futureTimestamp = ZonedDateTime.now().plusDays(5);
        ZonedDateTime futureFutureTimestamp = ZonedDateTime.now().plusDays(8);

        Course course1 = CourseFactory.generateCourse(courseId, pastTimestamp, futureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        ModelingExercise modelingExercise = ModelingExerciseFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.ClassDiagram,
                course1);
        modelingExercise.setGradingInstructions("Grading instructions");
        modelingExercise.getCategories().add("Modeling");
        modelingExercise.setId(exerciseId);
        course1.addExercises(modelingExercise);

        return modelingExercise;
    }

    /**
     * Creates a new modeling exercise submission with the passed model
     *
     * @param modelingExercise the exercise for which a submission should be generated
     * @param model            model of the submission
     * @param explanation      explanation of the submissions
     * @return the created modeling submission
     */
    public static ModelingSubmission generateModelingExerciseSubmission(ModelingExercise modelingExercise, String model, String explanation) {
        ModelingSubmission submission = new ModelingSubmission();
        StudentParticipation studentParticipation = new StudentParticipation();
        submission.setParticipation(studentParticipation);

        submission.setModel(model);
        submission.setExplanationText(explanation);

        modelingExercise.getStudentParticipations().add(studentParticipation);
        return submission;
    }
}
