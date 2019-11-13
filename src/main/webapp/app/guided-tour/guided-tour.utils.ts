import { Course } from 'app/entities/course';
import { Exercise } from 'app/entities/exercise';

/**
 * Helper function that triggers a click event on the defined element
 * @param selector: css selector to query the required element
 */
export function clickOnElement(selector: string): void {
    const htmlElement = document.querySelector(selector) as HTMLElement;
    if (htmlElement) {
        htmlElement.click();
    }
}

export function compareCourseShortName(course: Course, guidedTourCourse: Course | null): boolean {
    if (!course || !guidedTourCourse) {
        return false;
    }
    return course === guidedTourCourse;
}

export function compareExerciseShortName(exercise: Exercise, guidedTourExercise: Exercise | null): boolean {
    if (!exercise || !guidedTourExercise) {
        return false;
    }
    return exercise === guidedTourExercise;
}
