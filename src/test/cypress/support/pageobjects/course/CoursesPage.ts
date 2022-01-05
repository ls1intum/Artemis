/**
 * A class which encapsulates UI selectors and actions for the courses page (/courses).
 */
export class CoursesPage {
    openCourse(courseId: number) {
        cy.get(`#course-${courseId}-header`).click();
        cy.url().should('include', '/exercises');
    }
}
