/**
 * A class which encapsulates UI selectors and actions for the course exercise page.
 */
export class CourseExercisePage {
    search(term: string): void {
        cy.get('#exercise-search-input').type(term);
        cy.get('#exercise-search-button').click();
    }
}
