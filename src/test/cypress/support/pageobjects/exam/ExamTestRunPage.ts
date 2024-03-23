import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';

import { examStartEnd } from '../../artemis';
import { COURSE_BASE, DELETE, PATCH, POST } from '../../constants';
import { CypressCredentials } from '../../users';

/**
 * A class which encapsulates UI selectors and actions for the exam details page.
 */
export class ExamTestRunPage {
    /**
     * Creates a test run
     * @returns the query chainable if a test needs to access the response
     */
    confirmTestRun() {
        cy.intercept(POST, `${COURSE_BASE}/*/exams/*/test-run`).as('createTestRunQuery');
        cy.get('.modal-dialog #createTestRunButton').click();
        return cy.wait('@createTestRunQuery');
    }

    startParticipation(user: CypressCredentials, course: Course, exam: Exam, testRunId: number) {
        cy.login(user);
        this.openTestRunPage(course, exam);
        this.startTestRun(testRunId);
        cy.url().should('contain', `/course-management/${course.id}/exams/${exam.id}/test-runs/${testRunId}/conduction`);
        examStartEnd.startExam();
    }

    createTestRun() {
        cy.get('#createTestRunButton').click();
    }

    saveTestRun() {
        cy.intercept(PATCH, `${COURSE_BASE}/*/exams/*/student-exams/*/working-time`).as('updateTestRunQuery');
        cy.get('#save').click();
        return cy.wait('@updateTestRunQuery');
    }

    getTestRun(testRunId: number) {
        return cy.get(`#testrun-${testRunId}`);
    }

    getTestRunRibbon() {
        return cy.get('#testRunRibbon');
    }

    openTestRunPage(course: Course, exam: Exam) {
        cy.visit('/course-management/' + course.id + '/exams/' + exam.id + '/test-runs');
    }

    setWorkingTimeHours(hours: number) {
        cy.get('#workingTimeHours').clear().type(hours.toString());
    }

    setWorkingTimeMinutes(minutes: number) {
        cy.get('#workingTimeMinutes').clear().type(minutes.toString());
    }

    setWorkingTimeSeconds(seconds: number) {
        cy.get('#workingTimeSeconds').clear().type(seconds.toString());
    }

    getWorkingTime(testRunId: number) {
        return this.getTestRun(testRunId).find('.working-time');
    }

    getStarted(testRunId: number) {
        return this.getTestRun(testRunId).find('.started');
    }

    getSubmitted(testRunId: number) {
        return this.getTestRun(testRunId).find('.submitted');
    }

    getTestRunIdElement(testRunId: number) {
        return this.getTestRun(testRunId).find('.testrun-id');
    }

    changeWorkingTime(testRunId: number) {
        cy.get(`#testrun-${testRunId}`).find('.manage-worktime').click();
    }

    startTestRun(testRunId: number) {
        cy.get(`#testrun-${testRunId}`).find('.start-testrun').click();
    }

    deleteTestRun(testRunId: number) {
        cy.get(`#testrun-${testRunId}`).find('.delete-testrun').click();
        cy.get('#confirm-entity-name').type('Test Run');
        cy.intercept(DELETE, `${COURSE_BASE}/*/exams/*/test-run/*`).as('deleteTestRunQuery');
        cy.get('#delete').click();
        return cy.wait('@deleteTestRunQuery');
    }
}
