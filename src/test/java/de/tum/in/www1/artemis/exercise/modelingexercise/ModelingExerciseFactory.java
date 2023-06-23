package de.tum.in.www1.artemis.exercise.modelingexercise;

import java.time.ZonedDateTime;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.enumeration.DiagramType;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.modeling.ApollonDiagram;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
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

    public static ApollonDiagram generateApollonDiagram(DiagramType diagramType, String title) {
        ApollonDiagram apollonDiagram = new ApollonDiagram();
        apollonDiagram.setDiagramType(diagramType);
        apollonDiagram.setTitle(title);
        return apollonDiagram;
    }
}
