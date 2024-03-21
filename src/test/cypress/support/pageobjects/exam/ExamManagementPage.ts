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
     * @param courseID the id of the course
     * @param examID the id of the exam
     * @param timeout how long to wait for the assessment dashboard button
     */
    openAssessmentDashboard(courseID: number, examID: number, timeout = 60000) {
        cy.visit(`/course-management/${courseID}/exams`);
        cy.get(`#exercises-button-${examID}`, { timeout }).click();
    }

    /**
     * Opens the test run page.
     */
    openTestRun() {
        cy.get(`#testrun-button`).click();
    }

    verifySubmitted(courseID: number, examID: number, username: string) {
        cy.visit(`/course-management/${courseID}/exams/${examID}/student-exams`);
        cy.get('#student-exam').find('.datatable-body-row').filter(`:contains("${username}")`).find('.submitted').contains('Yes');
    }

    checkQuizSubmission(courseID: number, examID: number, username: string, score: string) {
        cy.visit(`/course-management/${courseID}/exams/${examID}/student-exams`);
        cy.get('#student-exam').find('.datatable-body-row').filter(`:contains("${username}")`).find('.view-submission').click();
        cy.get('.summery').click();
        cy.get('#exercise-result-score').contains(score);
    }

    clickEdit() {
        cy.get('#editButton').click();
    }

    /**
     * helper methods to get information of course
     * */
    getExamTitle() {
        return cy.get('#detail-value-artemisApp\\.exam\\.title');
    }

    getExamVisibleDate() {
        return cy.get('#detail-value-artemisApp\\.examManagement\\.visibleDate');
    }

    getExamStartDate() {
        return cy.get('#detail-value-artemisApp\\.exam\\.startDate');
    }

    getExamEndDate() {
        return cy.get('#detail-value-artemisApp\\.exam\\.endDate');
    }

    getExamNumberOfExercises() {
        return cy.get('#detail-value-artemisApp\\.examManagement\\.numberOfExercisesInExam');
    }

    getExamMaxPoints() {
        return cy.get('#detail-value-artemisApp\\.examManagement\\.maxPoints\\.title');
    }

    getExamStartText() {
        return cy.get('#detail-value-artemisApp\\.examManagement\\.startText');
    }

    getExamEndText() {
        return cy.get('#detail-value-artemisApp\\.examManagement\\.endText');
    }

    getExamConfirmationStartText() {
        return cy.get('#detail-value-artemisApp\\.examManagement\\.confirmationStartText');
    }

    getExamConfirmationEndText() {
        return cy.get('#detail-value-artemisApp\\.examManagement\\.confirmationEndText');
    }

    getExamWorkingTime() {
        return cy.get('#detail-value-artemisApp\\.exam\\.workingTime');
    }
}
