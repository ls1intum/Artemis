export default class CourseExercisePage {
    search(term: string): void {
        cy.get('#search-exercises-input').type(term);
        cy.get('#search-exercises-button').click();
    }
}
