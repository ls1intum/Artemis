import { CypressCredentials } from '../../users';

/**
 * A class which encapsulates UI selectors and actions for the course management page.
 */
export class CourseManagementPage {
    openCourseCreation() {
        return cy.get('#create-course').click();
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
        cy.get('#add-students').click();
        this.confirmUserIntoGroup(credentials);
    }

    /**
     * Adds the user to the tutor group of the course
     * @param credentials the user that gets added to the tutor group of the course
     * */
    addTutorToCourse(credentials: CypressCredentials) {
        cy.get('#add-tutors').click();
        this.confirmUserIntoGroup(credentials);
    }

    /**
     * Adds the user to the editor group of the course
     * @param credentials the user that gets added to the editor group of the course
     * */
    addEditorToCourse(credentials: CypressCredentials) {
        cy.get('#add-editors').click();
        this.confirmUserIntoGroup(credentials);
    }

    /**
     * Adds the user to the instructor group of the course
     * @param credentials the user that gets added to the instructor group of the course
     * */
    addInstructorToCourse(credentials: CypressCredentials) {
        cy.get('#add-instructors').click();
        this.confirmUserIntoGroup(credentials);
    }

    /**
     * helper method to avoid code duplication
     * */
    private confirmUserIntoGroup(credentials: CypressCredentials) {
        cy.get('#typeahead-basic ').type(credentials.username).type('{enter}');
        cy.get('#ngb-typeahead-0-0').contains(credentials.username).click();
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
}
