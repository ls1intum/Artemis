/**
 * A class which encapsulates UI selectors and actions for the exam management page.
 */
export class ExamManagementPage {
    /**
     * Searches for an exam with the provided title.
     * @param examTitle the title of the exam.
     * @returns the row element of the found exam
     */
    getExamRowRoot(examTitle: string) {
        return this.getExamSelector(examTitle).parents('tr');
    }

    /** Opens the exam with this exam id. */
    openExam(examId: number) {
        cy.get(`#exam-${examId}-title`).click();
    }

    /**
     * Clicks the create new exam button.
     */
    createNewExam() {
        cy.get('#create-exam').click();
    }

    /**
     * Returns the title element of the exam row.
     * @param examTitle the title to search for
     * @returns the element
     */
    getExamSelector(examTitle: string) {
        return cy.get('#exams-table').contains(examTitle);
    }

    /**
     * Opens the exercise groups page.
     */
    openExerciseGroups(examId: number) {
        cy.get(`#exercises-button-${examId}-groups`).click();
    }

    /**
     * Opens the student registration page.
     */
    openStudentRegistration(examId: number) {
        cy.get(`#student-button-${examId}`).click();
    }

    /**
     * Opens the student exams page.
     */
    openStudentExams(examId: number) {
        cy.get(`#student-exams-${examId}`).click();
    }

    /**
     * Opens the exam assessment dashboard
     * @param examId the id of the exam
     * @param timeout how long to wait for the assessment dashboard button
     */
    openAssessmentDashboard(examId: number, timeout: number) {
        cy.get('#exercises-button-' + examId, { timeout }).click();
    }

    /**
     * Opens the test run page.
     */
    openTestRun() {
        cy.get(`#testrun-button`).click();
    }

    /**
     * helper methods to get information of course
     * */
    getExamTitle() {
        return cy.get('#exam-detail-title');
    }

    getExamVisibleDate() {
        return cy.get('#exam-visible-date');
    }

    getExamStartDate() {
        return cy.get('#exam-start-date');
    }

    getExamEndDate() {
        return cy.get('#exam-end-date');
    }

    getExamNumberOfExercises() {
        return cy.get('#exam-number-of-exercises');
    }

    getExamMaxPoints() {
        return cy.get('#exam-max-points');
    }

    getExamStartText() {
        return cy.get('#exam-start-text');
    }

    getExamEndText() {
        return cy.get('#exam-end-text');
    }

    getExamConfirmationStartText() {
        return cy.get('#exam-confirmation-start-text');
    }

    getExamConfirmationEndText() {
        return cy.get('#exam-confirmation-end-text');
    }

    getExamWorkingTime() {
        return cy.get('#exam-working-time');
    }
}
