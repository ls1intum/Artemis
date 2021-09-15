import { artemis } from 'src/test/cypress/support/ArtemisTesting';
import { generateUUID } from 'src/test/cypress/support/utils';

// The user management object
const users = artemis.users;

// Requests
const courseManagement = artemis.requests.courseManagement;

// PageObjects
const textEditor = artemis.pageobjects.textExercise.editor;

describe('Text exercise participation', () => {
    let course: any;
    const exerciseTitle = 'Text exercise ' + generateUUID();

    before(() => {
        cy.login(users.getAdmin());
        courseManagement.createCourse().then((response) => {
            course = response.body;
            courseManagement.addStudentToCourse(course.id, users.getStudentOne().username);
            courseManagement.createTextExercise({ course }, exerciseTitle);
        });
    });

    it('Creates a text exercise in the UI', () => {
        cy.login(users.getStudentOne(), `/courses/${course.id}/exercises`);
        cy.contains('Start exercise').click();
        cy.get('[data-icon="folder-open"]').click();

        // Verify the initial state of the text editor
        cy.contains(exerciseTitle).should('be.visible');
        cy.get('[jhitranslate="artemisApp.exercise.problemStatement"]').should('be.visible');
        cy.get('.exercise-details-table').should('be.visible');
        cy.contains('No Submission').should('be.visible');

        // Make a submission
        cy.fixture('loremIpsum.txt').then((submission) => {
            textEditor.typeSubmission(submission);
            cy.contains('Number of words: 100').should('be.visible');
            cy.contains('Number of characters: 591').should('be.visible');
            textEditor
                .submit()
                .its('response')
                .then((response: any) => {
                    expect(response.body.text).equals(submission);
                    expect(response.body.submitted).equals(true);
                    expect(response.statusCode).equals(200);
                });
            cy.get('.alert-success').should('be.visible');
            cy.get('[jhitranslate="artemisApp.result.noResult"]').should('be.visible');
        });
    });

    after(() => {
        if (!!course) {
            cy.login(users.getAdmin());
            courseManagement.deleteCourse(course.id);
        }
    });
});
