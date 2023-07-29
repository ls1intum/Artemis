import { CourseManagementRequests } from './CourseManagementRequests';
import { UserManagementRequests } from './UserManagementRequest';

/**
 * A class which encapsulates all cypress requests, which can be sent to Artemis.
 */
export class ArtemisRequests {
    courseManagement = new CourseManagementRequests();
    userManagement = new UserManagementRequests();
}

export const courseManagementRequest = new CourseManagementRequests();
export const userManagementRequest = new UserManagementRequests();
