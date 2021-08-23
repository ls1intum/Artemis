/**
 * A class which encapsulates UI selectors and actions for the course management exercises page.
 */
export class CourseManagementExercisesPage {
    getExerciseRowRootElement(exerciseTitle: string) {
        return cy.contains(exerciseTitle).parents('tr');
    }

    clickDeleteExercise(exerciseTitle: string) {
        this.getExerciseRowRootElement(exerciseTitle).find('[deleteconfirmationtext="artemisApp.exercise.delete.typeNameToConfirm"]').click();
    }
}
