/**
 * A class which encapsulates UI selectors and actions for the course management exercises page.
 */
export class CourseManagementExercisesPage {
    readonly exerciseCardSelector = '#exercise-card-';

    getExerciseRowRootElement(exerciseId: number) {
        return cy.get(this.exerciseCardSelector + exerciseId);
    }

    clickDeleteExercise(exerciseId: number) {
        this.getExerciseRowRootElement(exerciseId).find('#delete-exercise').click();
    }

    clickCreateProgrammingExerciseButton() {
        cy.get('#jh-create-entity').click();
    }

    shouldContainExerciseWithName(exerciseId: string) {
        cy.get(this.exerciseCardSelector + exerciseId)
            .scrollIntoView()
            .should('be.visible');
    }
}
