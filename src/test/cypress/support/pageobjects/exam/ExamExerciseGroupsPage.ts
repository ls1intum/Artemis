/**
 * A class which encapsulates UI selectors and actions for the exam exercise groups page.
 */
export class ExamExerciseGroupsPage {
    clickCreateNewExerciseGroup() {
        cy.get('#create-new-group').click();
    }

    shouldShowNumberOfExerciseGroups(numberOfGroups: number) {
        cy.get('#number-groups').should('contain.text', numberOfGroups);
    }

    clickAddExerciseGroup() {
        cy.get('#create-new-group').click();
    }

    clickAddTextExercise() {
        cy.get('#add-text-exercise').click();
    }

    clickAddModelingExercise() {
        cy.get('#add-modeling-exercise').click();
    }

    clickAddQuizExercise() {
        cy.get('#add-quiz-exercise').click();
    }

    clickAddProgrammingExercise() {
        cy.get('#add-programming-exercise').click();
    }

    visitPageViaUrl(courseId: number, examId: number) {
        cy.visit(`course-management/${courseId}/exams/${examId}/exercise-groups`);
    }

    shouldContainExerciseWithTitle(exerciseTitle: string) {
        cy.get('#exercises').contains(exerciseTitle).scrollIntoView().should('be.visible');
    }
}
