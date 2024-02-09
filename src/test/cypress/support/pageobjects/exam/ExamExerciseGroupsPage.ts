/**
 * A class which encapsulates UI selectors and actions for the exam exercise groups page.
 */
export class ExamExerciseGroupsPage {
    clickCreateNewExerciseGroup() {
        cy.get('#create-new-group').click();
    }

    shouldHaveTitle(groupID: number, groupTitle: string) {
        cy.get(`#group-${groupID} .group-title`).contains(groupTitle);
    }

    shouldNotExist(groupID: number) {
        cy.get(`#group-${groupID}`).should('not.exist');
    }

    clickEditGroup(groupID: number) {
        cy.get(`#group-${groupID} .edit-group`).click();
    }

    clickDeleteGroup(groupID: number, groupName: string) {
        cy.get(`#group-${groupID} .delete-group`).click();
        cy.get('#delete').should('be.disabled');
        cy.get('#confirm-entity-name').type(groupName);
        cy.get('#delete').should('not.be.disabled').click();
    }

    shouldShowNumberOfExerciseGroups(numberOfGroups: number) {
        cy.get('#number-groups').should('contain.text', numberOfGroups);
    }

    clickAddExerciseGroup() {
        cy.get('#create-new-group').click();
    }

    clickAddTextExercise(groupID: number) {
        cy.get(`#group-${groupID}`).find('.add-text-exercise').click();
    }

    clickAddModelingExercise(groupID: number) {
        cy.get(`#group-${groupID}`).find('.add-modeling-exercise').click();
    }

    clickAddQuizExercise(groupID: number) {
        cy.get(`#group-${groupID}`).find('.add-quiz-exercise').click();
    }

    clickAddProgrammingExercise(groupID: number) {
        cy.get(`#group-${groupID}`).find('.add-programming-exercise').click();
    }

    visitPageViaUrl(courseId: number, examId: number) {
        cy.visit(`course-management/${courseId}/exams/${examId}/exercise-groups`);
    }

    shouldContainExerciseWithTitle(groupID: number, exerciseTitle: string) {
        cy.get(`#group-${groupID}`).find('#exercises').contains(exerciseTitle).scrollIntoView().should('be.visible');
    }
}
