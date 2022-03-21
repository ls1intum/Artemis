import dayjs from 'dayjs/esm';
import { dayjsToString } from '../../utils';

/**
 * A class which encapsulates UI selectors and actions for the exam creation page.
 */
export class ExamCreationPage {
    /**
     * Sets the title of the exam.
     * @param title the exam title
     */
    setTitle(title: string) {
        cy.get('#title').type(title);
    }

    /**
     * @param date the date from when the exam should be visible
     */
    setVisibleDate(date: dayjs.Dayjs) {
        this.enterDate('#visibleDate', date);
    }

    /**
     * @param date the date when the exam starts
     */
    setStartDate(date: dayjs.Dayjs) {
        this.enterDate('#startDate', date);
    }

    /**
     * @param date the date when the exam will end
     */
    setEndDate(date: dayjs.Dayjs) {
        this.enterDate('#endDate', date);
    }

    /**
     * Sets the number of exercises in the exam.
     * @param amount the amount of exercises
     */
    setNumberOfExercises(amount: number) {
        cy.get('#numberOfExercisesInExam').clear().type(amount.toString());
    }

    /**
     * Sets the maximum achievable points in the exam.
     * @param maxPoints the max points
     */
    setMaxPoints(maxPoints: number) {
        cy.get('#maxPoints').clear().type(maxPoints.toString());
    }

    /**
     * Sets the start text of the exam.
     * @param text the start text
     */
    setStartText(text: string) {
        this.enterText('#startText', text);
    }

    /**
     * Sets the end text of the exam.
     * @param text the end text
     */
    setEndText(text: string) {
        this.enterText('#endText', text);
    }

    /**
     * Sets the confirmation start text of the exam.
     * @param text the confirmation start text
     */
    setConfirmationStartText(text: string) {
        this.enterText('#confirmationStartText', text);
    }

    /**
     * Sets the confirmation end text of the exam.
     * @param text the confirmation end text
     */
    setConfirmationEndText(text: string) {
        this.enterText('#confirmationEndText', text);
    }

    /**
     * Submits the created exam.
     * @returns the query chainable if a test needs to access the response
     */
    submit() {
        cy.intercept('POST', '/api/courses/*/exams').as('examCreationQuery');
        cy.get('#save-exam').click();
        return cy.wait('@examCreationQuery');
    }

    private enterText(selector: string, text: string) {
        cy.get(selector).find('.ace_content').type(text);
    }

    private enterDate(selector: string, date: dayjs.Dayjs) {
        const dateInputField = cy.get(selector).find('#date-input-field');
        dateInputField.should('not.be.disabled');
        dateInputField.clear().type(dayjsToString(date), { force: true });
    }
}
