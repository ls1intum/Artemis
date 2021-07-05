/**
 * A class which encapsulates UI selectors and actions for the navigation bar at the top.
 */
export class NavigationBar {
    constructor() {
        cy.intercept('GET', '/api/courses/course-management-overview*').as('courseManagementQuery');
    }

    /**
     * Opens the course management page via the menu at the top and waits until it is loaded.
     */
    openCourseManagement() {
        cy.log('Opening course-management page...');
        cy.get('#course-admin-menu').should('be.visible').click();
        cy.wait('@courseManagementQuery', { timeout: 30000 });
        cy.url({ timeout: 10000 }).should('include', '/course-management');
    }
}
