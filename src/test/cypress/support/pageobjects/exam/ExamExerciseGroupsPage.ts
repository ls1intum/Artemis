/**
 * A class which encapsulates UI selectors and actions for the exam exercise groups page.
 */
export class ExamExerciseGroupsPage {
    clickCreateNewExerciseGroup() {
        cy.get('[jhitranslate="artemisApp.examManagement.exerciseGroup.create"]').click();
    }

    shouldShowNumberOfExerciseGroups(numberOfGroups: number) {
        cy.contains(`Number of exercise groups: ${numberOfGroups}`).should('be.visible');
    }

    clickAddExerciseGroup() {
        cy.get('[jhitranslate="artemisApp.examManagement.exerciseGroup.create"]').click();
    }

    clickAddTextExercise() {
        cy.contains('Add Text Exercise').click();
    }

    visitPageViaUrl(courseId: number, examId: number) {
        cy.visit(`course-management/${courseId}/exams/${examId}/exercise-groups`);
    }

    shouldContainExerciseWithTitle(exerciseTitle: string) {
        cy.get('.table').contains(exerciseTitle).scrollIntoView().should('be.visible');
    }
}
