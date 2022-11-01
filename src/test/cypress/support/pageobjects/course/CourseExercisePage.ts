export default class CourseExercisePage {
    search(term: string): void {
        cy.get('#exercise-search-input').type(term);
        cy.get('#exercise-search-button').click();
    }
}
