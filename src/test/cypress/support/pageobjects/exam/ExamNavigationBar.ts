/**
 * A class which encapsulates UI selectors and actions for the navigation bar in an open exam.
 */
export class ExamNavigationBar {
    /**
     * Opens the exercise at the specified index.
     * @param index 0-based index
     */
    openExerciseAtIndex(index: number) {
        this.clickNavigationItemAtIndex(index + 2);
    }

    /**
     * Presses the right arrow in the navigation bar.
     */
    navigateRight() {
        this.getNavigationItems().last().click();
    }

    /**
     * Presses the hand in early button in the navigation bar.
     */
    handInEarly() {
        this.getNavigationBarRoot().find('.btn-danger').click();
    }

    /**
     * Clicks the navigation item at the specified index.
     * @param index the navigation item index. If the exam has x exercises:
     *              index = 0 -> navigation overview, 1 -> left arrow, 2 -> first exercise, x + 1 -> last exercise, x + 2 -> right arrow
     */
    private clickNavigationItemAtIndex(index: number) {
        this.getNavigationItems().eq(index).click();
    }

    private getNavigationItems() {
        return this.getNavigationBarRoot().find('.navigation-item');
    }

    private getNavigationBarRoot() {
        return cy.get('#exam-navigation-bar');
    }
}
