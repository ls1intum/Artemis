/**
 * A class which encapsulates UI selectors and actions for the navigation bar in an open exam.
 */
export class ExamNavigationBar {
    /**
     * Clicks the navigation item at the specified index.
     * @param index the navigation item index. If the exam has x exercises: index = 0 -> left navigation arrow, 1 -> first exercise, x -> last exercise, x + 1 -> right arrow
     */
    clickNavigationItemAtIndex(index: number) {
        this.getNavigationBarRoot().find('.navigation-item').eq(index);
    }

    private getNavigationBarRoot() {
        return cy.get('#exam-navigation-bar');
    }
}
