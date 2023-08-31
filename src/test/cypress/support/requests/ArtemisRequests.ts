import { CommunicationAPIRequests } from './CommunicationAPIRequests';
import { CourseManagementAPIRequests } from './CourseManagementAPIRequests';
import { ExamAPIRequests } from './ExamAPIRequests';
import { ExerciseAPIRequests } from './ExerciseAPIRequests';
import { UserManagementAPIRequests } from './UserManagementAPIRequest';

/**
 * A class which encapsulates all cypress requests, which can be sent to Artemis.
 */
export class ArtemisRequests {
    communication = new CommunicationAPIRequests();
    courseManagement = new CourseManagementAPIRequests();
    exam = new ExamAPIRequests();
    exercise = new ExerciseAPIRequests();
    userManagement = new UserManagementAPIRequests();
}
