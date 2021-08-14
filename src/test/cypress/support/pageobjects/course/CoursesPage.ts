/**
 * A class which encapsulates UI selectors and actions for the courses page (/courses).
 */
export class CoursesPage {
    openCourse(courseName: string) {
        cy.contains(courseName).parents('.card-header').click();
        cy.url().should('include', '/exercises');
    }
}
