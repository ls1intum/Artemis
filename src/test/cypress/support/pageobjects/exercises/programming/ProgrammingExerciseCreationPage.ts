import { POST, PROGRAMMING_EXERCISE_BASE, ProgrammingLanguage } from '../../../constants';
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
        cy.intercept(POST, `${PROGRAMMING_EXERCISE_BASE}/setup`).as('createProgrammingExercise');
        cy.get('#save-entity').click();
        // Creating a programming exercise can take quite a while, so we increase the default timeout here
        return cy.wait('@createProgrammingExercise', { timeout: 60000 });
    }

    import() {
        cy.intercept(POST, `${PROGRAMMING_EXERCISE_BASE}/import/*`).as('programmingExerciseImport');
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

        // Makes sure that popup is visible before we choose a date
        cy.get('.owl-dt-popup').should('be.visible');

        const ariaLabelDate = date.format(OWL_DATEPICKER_ARIA_LABEL_DATE_FORMAT);
        cy.get(`td[aria-label="${ariaLabelDate}"]`).click();

        // There is a race condition, where an event listener already attaches and is ready to process the actual click on a date element
        // but no event listener has been attached yet to close the modal. Thus, we need to keep clicking until the calendar modal is closed.
        // Another idea would be to use cy.wait(), however it's not recommended by the developers.
        // Cypress-pipe does not retry any Cypress commands so we need to click on the element using jQuery method "$el.trigger('click')" and not "cy.click()"
        // See https://www.cypress.io/blog/2019/01/22/when-can-the-test-click for more information
        const click = ($el: JQuery<HTMLElement>) => $el.trigger('click');

        cy.get('.owl-dt-control-content.owl-dt-control-button-content')
            .should('be.visible')
            .contains('Set')
            .pipe(click)
            .should(($el) => {
                expect($el).to.not.be.visible;
            });
    }
}
