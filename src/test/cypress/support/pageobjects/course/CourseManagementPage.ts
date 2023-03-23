import { POST } from '../../constants';
import { COURSE_BASE } from '../../requests/CourseManagementRequests';
import { CypressCredentials } from '../../users';

/**
 * A class which encapsulates UI selectors and actions for the course management page.
 */
export class CourseManagementPage {
    openCourseCreation() {
        return cy.get('#create-course').click();
    }

    openCourseEdit() {
        return cy.get('#edit-course').click();
    }

    /**
     * @returns Returns the cypress chainable containing the root element of the course card of our created course.
     * This can be used to find specific elements within this course card.
     */
    getCourseCard(courseShortName: string) {
        return cy.get('#course-card-' + courseShortName);
    }

    /**
     * Opens the exercises (of the first found course).
     */
    openExercisesOfCourse(courseShortName: string) {
        this.getCourseCard(courseShortName).find('#course-card-open-exercises').click();
        cy.url().should('include', '/exercises');
    }

    /**
     * Opens the students overview page of a course.
     * @param courseId the id of the course
     */
    openStudentOverviewOfCourse(courseId: number) {
        cy.get('#open-student-management-' + courseId).click();
    }

    /**
     * Opens a course.
     * @param courseShortName
     */
    openCourse(courseShortName: string) {
        return this.getCourseCard(courseShortName).find('#course-card-header').click();
    }

    /**
     * Adds the user to the student group of the course
     * @param credentials the user that gets added to the student group of the course
     * */
    addStudentToCourse(credentials: CypressCredentials) {
        cy.intercept(POST, COURSE_BASE + '*/students/' + credentials.username).as('addStudentQuery');
        cy.get('#add-students').click();
        this.confirmUserIntoGroup(credentials);
        cy.wait('@addStudentQuery');
    }

    /**
     * Adds the user to the tutor group of the course
     * @param credentials the user that gets added to the tutor group of the course
     * */
    addTutorToCourse(credentials: CypressCredentials) {
        cy.intercept(POST, COURSE_BASE + '*/tutors/' + credentials.username).as('addTutorsQuery');
        cy.get('#add-tutors').click();
        this.confirmUserIntoGroup(credentials);
        cy.wait('@addTutorsQuery');
    }

    /**
     * Adds the user to the editor group of the course
     * @param credentials the user that gets added to the editor group of the course
     * */
    addEditorToCourse(credentials: CypressCredentials) {
        cy.intercept(POST, COURSE_BASE + '*/editors/' + credentials.username).as('addEditorsQuery');
        cy.get('#add-editors').click();
        this.confirmUserIntoGroup(credentials);
        cy.wait('@addEditorsQuery');
    }

    /**
     * Adds the user to the instructor group of the course
     * @param credentials the user that gets added to the instructor group of the course
     * */
    addInstructorToCourse(credentials: CypressCredentials) {
        cy.intercept(POST, COURSE_BASE + '*/instructors/' + credentials.username).as('addInstructorQuery');
        cy.get('#add-instructors').click();
        this.confirmUserIntoGroup(credentials);
        cy.wait('@addInstructorQuery');
    }

    /**
     * helper method to avoid code duplication
     * */
    private confirmUserIntoGroup(credentials: CypressCredentials) {
        cy.get('#typeahead-basic ').type(credentials.username).type('{enter}');
        cy.get('#ngb-typeahead-0')
            .contains(new RegExp('\\(' + credentials.username + '\\)'))
            .click();
        cy.get('#bread-crumb-2').click();
    }

    /**
     * Opens the exams of a course.
     */
    openExamsOfCourse(courseShortName: string) {
        this.getCourseCard(courseShortName).find('#course-card-open-exams').click();
        cy.url().should('include', '/exams');
    }

    openAssessmentDashboardOfCourse(courseShortName: string) {
        this.getCourseCard(courseShortName).find('#course-card-open-assessment-dashboard').click();
        cy.url().should('include', '/assessment-dashboard');
    }

    /**
     * helper methods to get information about the course
     * */
    getCourseHeaderTitle() {
        return cy.get('#course-header-title');
    }

    getCourseHeaderDescription() {
        return cy.get('#course-header-description');
    }

    getCourseTitle() {
        return cy.get('#course-title');
    }

    getCourseShortName() {
        return cy.get('#course-short-name');
    }

    getCourseStudentGroupName() {
        return cy.get('#course-student-group-name');
    }

    getCourseTutorGroupName() {
        return cy.get('#course-tutor-group-name');
    }

    getCourseEditorGroupName() {
        return cy.get('#course-editor-group-name');
    }

    getCourseInstructorGroupName() {
        return cy.get('#course-instructor-group-name');
    }

    getCourseStartDate() {
        return cy.get('#course-start-date');
    }

    getCourseEndDate() {
        return cy.get('#course-end-date');
    }

    getCourseSemester() {
        return cy.get('#course-semester');
    }

    getCourseProgrammingLanguage() {
        return cy.get('#course-programming-language');
    }

    getCourseTestCourse() {
        return cy.get('#course-test-course');
    }

    getCourseOnlineCourse() {
        return cy.get('#course-online-course');
    }

    getCoursePresentationScoreEnabled() {
        return cy.get('#course-presentation-score-enabled');
    }

    getCoursePresentationScore() {
        return cy.get('#course-presentation-score');
    }

    getCourseMaxComplaints() {
        return cy.get('#course-max-complaints');
    }

    getCourseMaxTeamComplaints() {
        return cy.get('#course-max-team-complaints');
    }

    getMaxComplaintTimeDays() {
        return cy.get('#course-max-time-days');
    }

    getMaxRequestMoreFeedbackTimeDays() {
        return cy.get('#course-max-request-more-feedback-days');
    }
}
