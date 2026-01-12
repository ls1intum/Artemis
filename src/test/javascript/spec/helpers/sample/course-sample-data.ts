import { Course } from 'app/core/course/shared/entities/course.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { UMLDiagramType } from '@ls1intum/apollon';

/**
 * Creates a sample course with default test data for use in unit tests.
 * @returns An object containing the course and its exercises
 */
export function createSampleCourse(): { course: Course; exercises: Exercise[] } {
    const course = new Course();
    course.id = 1234;
    course.title = 'testTitle';
    const exercises: Exercise[] = [
        new ModelingExercise(UMLDiagramType.ComponentDiagram, undefined, undefined),
        new ModelingExercise(UMLDiagramType.ComponentDiagram, undefined, undefined),
    ];
    course.exercises = exercises;
    course.lectures = undefined;
    course.startDate = undefined;
    course.endDate = undefined;
    course.competencies = [];
    course.prerequisites = [];
    return { course, exercises };
}
