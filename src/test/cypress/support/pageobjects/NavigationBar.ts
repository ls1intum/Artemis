import { COURSE_BASE } from '../requests/CourseManagementRequests';
import { GET } from '../constants';
/**
 * A class which encapsulates UI selectors and actions for the navigation bar at the top.
 */
export class NavigationBar {
    /**
     * Opens the course management page via the menu at the top and waits until it is loaded.
     */
    openCourseManagement() {
        cy.log('Opening course-management page...');
        cy.intercept(GET, COURSE_BASE + 'course-management-overview*').as('courseManagementQuery');
        cy.get('#course-admin-menu').click();
        cy.wait('@courseManagementQuery', { timeout: 30000 });
        cy.url().should('include', '/course-management');
    }
}
