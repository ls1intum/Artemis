import { BASE_API, PUT } from '../../constants';
import { POST } from '../../constants';

/**
 * A class which encapsulates UI selectors and actions for the exam exercise group creation page.
 */
export class ExamExerciseGroupCreationPage {
    typeTitle(title: string) {
        cy.get('#title').clear().type(title);
    }

    isMandatoryBoxShouldBeChecked() {
        cy.get('#isMandatory').should('be.checked');
    }

    clickSave() {
        cy.intercept({ method: POST, url: `${BASE_API}courses/*/exams/*/exerciseGroups` }).as('createExerciseGroup');
        cy.get('#save-group').click();
        cy.wait('@createExerciseGroup');
    }

    update() {
        cy.intercept({ method: PUT, url: `${BASE_API}courses/*/exams/*/exerciseGroups` }).as('updateExerciseGroup');
        cy.get('#save-group').click();
        cy.wait('@updateExerciseGroup');
    }
}
