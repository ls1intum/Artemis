import dayjs from 'dayjs/esm';

import { COURSE_BASE, POST, PUT } from '../../constants';
import { enterDate } from '../../utils';

/**
 * A class which encapsulates UI selectors and actions for the exam creation page.
 */
export class ExamCreationPage {
    /**
     * Sets the title of the exam.
     * @param title the exam title
     */
    setTitle(title: string) {
        cy.get('#field_title').clear().type(title);
    }

    /**
     * Sets exam to test mode
     */
    setTestMode() {
        cy.get('#exam-mode-picker #test-mode').click();
    }

    /**
     * @param date the date from when the exam should be visible
     */
    setVisibleDate(date: dayjs.Dayjs) {
        enterDate('#visibleDate', date);
    }

    /**
     * @param date the date when the exam starts
     */
    setStartDate(date: dayjs.Dayjs) {
        enterDate('#startDate', date);
    }

    /**
     * @param date the date when the exam will end
     */
    setEndDate(date: dayjs.Dayjs) {
        enterDate('#endDate', date);
    }

    /**
     * @param time the exam working time
     */
    setWorkingTime(time: number) {
        cy.get('#workingTimeInMinutes').clear().type(time.toString());
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
     * @param examMaxPoints the max points
     */
    setExamMaxPoints(examMaxPoints: number) {
        cy.get('#examMaxPoints').clear().type(examMaxPoints.toString());
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
        cy.intercept(POST, `${COURSE_BASE}/*/exams`).as('createExamQuery');
        cy.get('#save-exam').click();
        return cy.wait('@createExamQuery');
    }

    /**
     * Updates the created exam.
     * @returns the query chainable if a test needs to access the response
     */
    update() {
        cy.intercept(PUT, `${COURSE_BASE}/*/exams`).as('updateExamQuery');
        cy.get('#save-exam').click();
        return cy.wait('@updateExamQuery');
    }

    private enterText(selector: string, text: string) {
        cy.get(selector).find('.ace_text-input').focus().clear().type(text);
    }
}
