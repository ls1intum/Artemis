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

    createProgrammingExercise() {
        cy.get('#create-programming-exercise').click();
    }

    createModelingExercise() {
        cy.get('#create-modeling-exercise').click();
    }

    createTextExercise() {
        cy.get('#create-text-exercise').click();
    }

    createQuizExercise() {
        cy.get('#create-quiz-button').click();
    }

    importProgrammingExercise() {
        cy.get('#import-programming-exercise').click();
    }

    importModelingExercise() {
        cy.get('#import-modeling-exercise').click();
    }

    importTextExercise() {
        cy.get('#import-text-exercise').click();
    }

    importQuizExercise() {
        cy.get('#import-quiz-exercise').click();
    }

    clickImportExercise(exerciseID: number) {
        return cy.get(`.exercise-${exerciseID}`).find('.import').click();
    }

    startQuiz(quizID: number) {
        cy.get(`#instructor-quiz-start-${quizID}`).click();
    }

    shouldContainExerciseWithName(exerciseId: string) {
        cy.get(this.exerciseCardSelector + exerciseId)
            .scrollIntoView()
            .should('be.visible');
    }
}
