import { COURSE_BASE, GET } from '../constants';

/**
 * A class which encapsulates UI selectors and actions for the navigation bar at the top.
 */
export class NavigationBar {
    /**
     * Opens the course management page via the menu at the top and waits until it is loaded.
     */
    openCourseManagement() {
        cy.log('Opening course-management page...');
        cy.intercept(GET, `${COURSE_BASE}/course-management-overview*`).as('courseManagementQuery');
        cy.get('#course-admin-menu').click();
        cy.wait('@courseManagementQuery', { timeout: 30000 });
        cy.url().should('include', '/course-management');
    }

    openNotificationPanel() {
        cy.get('.navbar .notification-button').click();
    }

    getNotifications() {
        return cy.get('.notification-sidebar .notification-item');
    }

    getAccountItem() {
        return cy.get('#account-menu');
    }

    logout() {
        this.getAccountItem().click().get('#logout').click();
    }
}
