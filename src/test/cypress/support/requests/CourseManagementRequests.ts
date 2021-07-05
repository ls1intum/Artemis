import { BASE_API, DELETE } from '../constants';

const BASE = BASE_API + 'courses/';

/**
 * A class which encapsulates all cypress requests related to course management.
 */
export class CourseManagementRequests {
    deleteCourse(id: number) {
        return cy.request({ method: DELETE, url: BASE + id });
    }
}
