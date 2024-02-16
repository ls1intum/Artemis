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

    /**
     * Generates a ModelingExercise for a Course.
     *
     * @param releaseDate       The release date of the exercise
     * @param dueDate           The due date of the exercise
     * @param assessmentDueDate The assessment due date of the exercise
     * @param diagramType       The DiagramType of the exercise
     * @param course            The Course the exercise belongs to
     * @return The generated ModelingExercise
     */
    public static ModelingExercise generateModelingExercise(ZonedDateTime releaseDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, DiagramType diagramType,
            Course course) {
        var modelingExercise = (ModelingExercise) ExerciseFactory.populateExercise(new ModelingExercise(), releaseDate, dueDate, assessmentDueDate, course);
        modelingExercise.setDiagramType(diagramType);
        modelingExercise.setExampleSolutionModel("This is my example solution model");
        modelingExercise.setExampleSolutionExplanation("This is my example solution model");
        return modelingExercise;
    }

    /**
     * Generates a ModelingExercise for an Exam.
     *
     * @param diagramType   The DiagramType of the exercise
     * @param exerciseGroup The Exam's ExerciseGroup the exercise belongs to
     * @return The generated ModelingExercise
     */
    public static ModelingExercise generateModelingExerciseForExam(DiagramType diagramType, ExerciseGroup exerciseGroup) {
        var modelingExercise = (ModelingExercise) ExerciseFactory.populateExerciseForExam(new ModelingExercise(), exerciseGroup);
        modelingExercise.setDiagramType(diagramType);
        modelingExercise.setExampleSolutionModel("This is my example solution model");
        modelingExercise.setExampleSolutionExplanation("This is my example solution model");
        return modelingExercise;
    }

    /**
     * Generates a ModelingExercise for an Exam.
     *
     * @param diagramType   The DiagramType of the exercise
     * @param exerciseGroup The Exam's ExerciseGroup the exercise belongs to
     * @param title         The title of the exercise
     * @return The generated ModelingExercise
     */
    public static ModelingExercise generateModelingExerciseForExam(DiagramType diagramType, ExerciseGroup exerciseGroup, String title) {
        var modelingExercise = (ModelingExercise) ExerciseFactory.populateExerciseForExam(new ModelingExercise(), exerciseGroup, title);
        modelingExercise.setDiagramType(diagramType);
        modelingExercise.setExampleSolutionModel("This is my example solution model");
        modelingExercise.setExampleSolutionExplanation("This is my example solution model");
        return modelingExercise;
    }

    /**
     * Generates an ApollonDiagram with the given DiagramType. The diagram is empty as its jsonRepresentation is not set.
     *
     * @param diagramType The DiagramType of the ApollonDiagram
     * @param title       The title of the ApollonDiagram
     * @return The generated ApollonDiagram
     */
    public static ApollonDiagram generateApollonDiagram(DiagramType diagramType, String title) {
        ApollonDiagram apollonDiagram = new ApollonDiagram();
        apollonDiagram.setDiagramType(diagramType);
        apollonDiagram.setTitle(title);
        return apollonDiagram;
    }

    /**
     * Generates a ModelingExercise for a Course.
     *
     * @param courseId The id of the Course the exercise belongs to
     * @return The generated ModelingExercise
     */
    public static ModelingExercise createModelingExercise(Long courseId) {
        return createModelingExercise(courseId, null);
    }

    /**
     * Generates a ModelingExercise for a Course.
     *
     * @param courseId   The id of the Course the exercise belongs to
     * @param exerciseId The id of the Exercise
     * @return The generated ModelingExercise
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
     * Generates a ModelingSubmission and StudentParticipation for the given ModelingExercise.
     *
     * @param modelingExercise The ModelingExercise the submission belongs to
     * @param model            The model of the submission
     * @param explanation      The explanation of the submission
     * @return The generated ModelingSubmission
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
