/**
 * A class which encapsulates UI selectors and actions for the course management page.
 */
export class CourseManagementPage {
    openCourseCreation() {
        return cy.get('.create-course').click();
    }

    /**
     * @returns Returns the cypress chainable containing the root element of the course card of our created course.
     * This can be used to find specific elements within this course card.
     */
    getCourseCard(courseName: string, courseShortName: string) {
        return cy.contains(this.courseSelector(courseName, courseShortName)).parent().parent().parent();
    }

    /**
     * Opens the exercises (of the first found course).
     */
    openExercisesOfCourse(courseName: string, courseShortName: string) {
        this.getCourseCard(courseName, courseShortName).find('.card-footer').children().eq(0).click();
        cy.url().should('include', '/exercises');
    }

    /**
     * Opens the students overview page of a course.
     * @param courseName
     * @param courseShortName
     */
    openStudentOverviewOfCourse(courseName: string, courseShortName: string) {
        // TODO: Generify the selector
        this.getCourseCard(courseName, courseShortName).contains('0 Students').click();
    }

    /**
     * Opens a course.
     * @param courseName
     * @param courseShortName
     */
    openCourse(courseName: string, courseShortName: string) {
        return cy.contains(this.courseSelector(courseName, courseShortName)).parent().parent().click();
    }

    /**
     * Retrieves the course selector. This is returns the element, which is used to identify a course card. It does not return the root of the course card!
     * @param courseName the title of the course
     * @param courseShortName the short name of the course
     * @returns the title element (not the root element!) of the course card.
     */
    courseSelector(courseName: string, courseShortName: string) {
        return `${courseName} (${courseShortName})`;
    }

    /**
     * Opens the exams of a course.
     */
    openExamsOfCourse(courseName: string, courseShortName: string) {
        this.getCourseCard(courseName, courseShortName).find('.card-footer').children().eq(1).click();
        cy.url().should('include', '/exams');
    }
}
