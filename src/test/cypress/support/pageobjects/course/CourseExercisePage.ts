export default class CourseExercisePage {
    search(term: string): void {
        cy.get('#search-exercises').type(term + '{enter}');
    }
}
