/**
 * A class which encapsulates UI selectors and actions for the navigation bar in an open exam.
 */
export class ExamNavigationBar {
    /**
     * Opens the exercise at the specified index.
     * @param index 0-based index
     */
    openExerciseAtIndex(index: number) {
        cy.get('#exam-exercise-' + index).click();
    }

    openExerciseOverview() {
        cy.get('.exam-navigation .navigation-item.overview').click();
    }

    /**
     * Presses the hand in early button in the navigation bar.
     */
    handInEarly() {
        cy.get('#hand-in-early').click();
    }

    clickSave() {
        cy.get('#save');
    }
}
