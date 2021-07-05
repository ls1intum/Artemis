import { CourseManagementRequests } from './CourseManagementRequests';

/**
 * A class which encapsulates all cypress requests, which can be sent to Artemis.
 */
export class ArtemisRequests {
    courseManagement = new CourseManagementRequests();
}
