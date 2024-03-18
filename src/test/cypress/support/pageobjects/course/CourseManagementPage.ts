import { Course } from 'app/entities/course.model';

import { COURSE_ADMIN_BASE, COURSE_BASE, DELETE, POST, PUT } from '../../constants';
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
    getCourse(courseID: number) {
        return cy.get(`#course-${courseID}`);
    }

    /**
     * Opens the exercises (of the first found course).
     */
    openExercisesOfCourse(courseID: number) {
        this.getCourse(courseID).find('#course-card-open-exercises').click();
        cy.url().should('include', '/exercises');
    }

    /**
     * Opens the students overview page of a course.
     * @param courseId the id of the course
     */
    openStudentOverviewOfCourse(courseId: number) {
        cy.get(`#open-student-management-${courseId}`).click();
    }

    /**
     * Opens a course.
     * @param courseID
     */
    openCourse(courseID: number) {
        return this.getCourse(courseID).find('#course-card-header').click();
    }

    deleteCourse(course: Course) {
        cy.get('#delete-course').click();
        cy.get('#delete').should('be.disabled');
        cy.get('#confirm-entity-name').type(course.title!);
        cy.intercept(DELETE, `${COURSE_ADMIN_BASE}/${course.id}`).as('deleteCourse');
        cy.get('#delete').click();
        cy.wait('@deleteCourse');
    }

    /**
     * Adds the user to the student group of the course
     * @param credentials the user that gets added to the student group of the course
     * */
    addStudentToCourse(credentials: CypressCredentials) {
        cy.intercept(POST, `${COURSE_BASE}/*/students/${credentials.username}`).as('addStudentQuery');
        cy.get('#detail-value-artemisApp\\.course\\.studentGroupName').children().first().click({ force: true });
        this.confirmUserIntoGroup(credentials);
        cy.wait('@addStudentQuery');
    }

    /**
     * Adds the user to the tutor group of the course
     * @param credentials the user that gets added to the tutor group of the course
     * */
    addTutorToCourse(credentials: CypressCredentials) {
        cy.intercept(POST, `${COURSE_BASE}/*/tutors/${credentials.username}`).as('addTutorsQuery');
        cy.get('#add-tutors').click();
        this.confirmUserIntoGroup(credentials);
        cy.wait('@addTutorsQuery');
    }

    /**
     * Adds the user to the editor group of the course
     * @param credentials the user that gets added to the editor group of the course
     * */
    addEditorToCourse(credentials: CypressCredentials) {
        cy.intercept(POST, `${COURSE_BASE}/*/editors/${credentials.username}`).as('addEditorsQuery');
        cy.get('#add-editors').click();
        this.confirmUserIntoGroup(credentials);
        cy.wait('@addEditorsQuery');
    }

    /**
     * Adds the user to the instructor group of the course
     * @param credentials the user that gets added to the instructor group of the course
     * */
    addInstructorToCourse(credentials: CypressCredentials) {
        cy.intercept(POST, `${COURSE_BASE}/*/instructors/${credentials.username}`).as('addInstructorQuery');
        cy.get('#add-instructors').click();
        this.confirmUserIntoGroup(credentials);
        cy.wait('@addInstructorQuery');
    }

    removeFirstUser() {
        cy.get('#registered-students button[jhideletebutton]').click();
        cy.get('.modal #delete').click();
    }

    clickEditCourse() {
        cy.get('#edit-course').click();
    }

    updateCourse(course: Course) {
        cy.intercept(PUT, `${COURSE_BASE}/${course.id}`).as('updateCourseQuery');
        cy.get('#save-entity').click();
        return cy.wait('@updateCourseQuery');
    }

    checkCourseHasNoIcon() {
        cy.get('#delete-course-icon').should('not.exist');
        cy.get('.no-image').should('exist');
    }

    removeIconFromCourse() {
        cy.get('#delete-course-icon').click();
        cy.get('#delete-course-icon').should('not.exist');
        cy.get('.no-image').should('exist');
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
    openExamsOfCourse(courseID: number) {
        this.getCourse(courseID).find('#course-card-open-exams').click();
        cy.url().should('include', '/exams');
    }

    openAssessmentDashboardOfCourse(courseID: number) {
        this.getCourse(courseID).find('#course-card-open-assessment-dashboard').click();
        cy.url().should('include', '/assessment-dashboard');
    }

    /**
     * helper methods to get information about the course
     * */

    getRegisteredStudents() {
        return cy.get('#registered-students');
    }

    getCourseHeaderTitle() {
        return cy.get('#course-header-title');
    }

    getCourseHeaderDescription() {
        return cy.get('#course-header-description');
    }

    getCourseTitle() {
        return cy.get('#detail-value-artemisApp\\.course\\.title');
    }

    getCourseShortName() {
        return cy.get('#detail-value-artemisApp\\.course\\.shortName');
    }

    getCourseStudentGroupName() {
        return cy.get('#detail-value-artemisApp\\.course\\.studentGroupName');
    }

    getCourseTutorGroupName() {
        return cy.get('#detail-value-artemisApp\\.course\\.teachingAssistantGroupName');
    }

    getCourseEditorGroupName() {
        return cy.get('#detail-value-artemisApp\\.course\\.editorGroupName');
    }

    getCourseInstructorGroupName() {
        return cy.get('#detail-value-artemisApp\\.course\\.instructorGroupName');
    }

    getCourseStartDate() {
        return cy.get('#detail-value-artemisApp\\.course\\.startDate');
    }

    getCourseEndDate() {
        return cy.get('#detail-value-artemisApp\\.course\\.endDate');
    }

    getCourseSemester() {
        return cy.get('#detail-value-artemisApp\\.course\\.semester');
    }

    getCourseProgrammingLanguage() {
        return cy.get('#detail-value-artemisApp\\.course\\.defaultProgrammingLanguage');
    }

    getCourseTestCourse() {
        return cy.get('#detail-value-artemisApp\\.course\\.testCourse\\.title');
    }

    getCourseOnlineCourse() {
        return cy.get('#detail-value-artemisApp\\.course\\.onlineCourse\\.title');
    }

    getCourseMaxComplaints() {
        return cy.get('#detail-value-artemisApp\\.course\\.maxComplaints\\.title');
    }

    getCourseMaxTeamComplaints() {
        return cy.get('#detail-value-artemisApp\\.course\\.maxTeamComplaints\\.title');
    }

    getMaxComplaintTimeDays() {
        return cy.get('#detail-value-artemisApp\\.course\\.maxComplaintTimeDays\\.title');
    }

    getMaxRequestMoreFeedbackTimeDays() {
        return cy.get('#detail-value-artemisApp\\.course\\.maxRequestMoreFeedbackTimeDays\\.title');
    }
}
