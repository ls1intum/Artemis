import { BASE_API, POST, PROGRAMMING_EXERCISE_BASE, ProgrammingLanguage } from '../../../constants';
import { Dayjs } from 'dayjs/esm';

const OWL_DATEPICKER_ARIA_LABEL_DATE_FORMAT = 'MMMM D, YYYY';

/**
 * A class which encapsulates UI selectors and actions for the programming exercise creation page.
 */
export class ProgrammingExerciseCreationPage {
    /**
     * @param title the title of the programming exercise
     */
    setTitle(title: string) {
        cy.get('#field_title').clear().type(title);
    }

    /**
     * @param shortName the short name of the programming exercise
     */
    setShortName(shortName: string) {
        cy.get('#field_shortName').clear().type(shortName);
    }

    /**
     * @param programmingLanguage the programming language of the programming exercise
     */
    setProgrammingLanguage(programmingLanguage: ProgrammingLanguage) {
        cy.get('#field_programmingLanguage').select(programmingLanguage);
    }

    /**
     * @param packageName the package name of the programming exercise
     */
    setPackageName(packageName: string) {
        cy.get('#field_packageName').clear().type(packageName);
    }

    /**
     * @param points Achievable points in the exercise
     */
    setPoints(points: number) {
        cy.get('#field_points').clear().type(points.toString());
    }

    /**
     * Allows the usage of the online editor.
     */
    checkAllowOnlineEditor() {
        cy.get('#field_allowOnlineEditor').check();
    }

    /**
     * Generates the programming exercise.
     * @returns the chainable of the request to make further verifications
     */
    generate() {
        cy.intercept(POST, PROGRAMMING_EXERCISE_BASE + 'setup').as('createProgrammingExercise');
        cy.get('#save-entity').click();
        // Creating a programming exercise can take quite a while, so we increase the default timeout here
        return cy.wait('@createProgrammingExercise', { timeout: 60000 });
    }

    import() {
        cy.intercept(POST, BASE_API + 'programming-exercises/import/*').as('programmingExerciseImport');
        cy.get('#save-entity').click();
        // Creating a programming exercise can take quite a while, so we increase the default timeout here
        return cy.wait('@programmingExerciseImport', { timeout: 60000 });
    }

    /**
     * Sets the Due Date field by using the owl datepicker
     * @param date
     * */
    setDueDate(date: Dayjs) {
        cy.get('#programming-exercise-due-date-picker').click();

        // Important to make sure that all event listeners are registered, see https://www.cypress.io/blog/2019/01/22/when-can-the-test-click for more information
        cy.get('.owl-dt-popup').should('be.visible');

        const ariaLabelDate = date.format(OWL_DATEPICKER_ARIA_LABEL_DATE_FORMAT);
        cy.get(`td[aria-label="${ariaLabelDate}"]`).click();

        cy.get('.owl-dt-control-content.owl-dt-control-button-content').contains('Set').should('exist').click();
    }
}
