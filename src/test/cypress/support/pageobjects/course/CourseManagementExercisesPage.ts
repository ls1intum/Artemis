/**
 * A class which encapsulates UI selectors and actions for the course management exercises page.
 */
export class CourseManagementExercisesPage {
    readonly exerciseCardSelector = '#exercise-card';

    getExerciseRowRootElement(exerciseTitle: string) {
        return cy.get(this.exerciseCardSelector).contains(exerciseTitle).parents('tr');
    }

    clickDeleteExercise(exerciseTitle: string) {
        this.getExerciseRowRootElement(exerciseTitle).find('#delete-exercise').click();
    }

    clickCreateProgrammingExerciseButton() {
        cy.get('#jh-create-entity').click();
    }

    shouldContainExerciseWithName(exerciseTitle: string) {
        cy.get(this.exerciseCardSelector).contains(exerciseTitle).scrollIntoView().should('be.visible');
    }
}
