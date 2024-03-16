import dayjs from 'dayjs/esm';

import { Course, CourseInformationSharingConfiguration } from 'app/entities/course.model';

import lectureTemplate from '../../fixtures/lecture/template.json';
import { BASE_API, COURSE_ADMIN_BASE, COURSE_BASE, DELETE, POST } from '../constants';
import { CypressCredentials } from '../users';
import { generateUUID, titleLowercase } from '../utils';

/**
 * A class which encapsulates all API requests related to course management.
 */
export class CourseManagementAPIRequests {
    /**
     * Creates a course with the specified title and short name.
     * @param options An object containing the options for creating the course
     *   - customizeGroups: whether the predefined groups should be used (so we don't have to wait more than a minute between course and programming exercise creation)
     *   - courseName: the title of the course (will generate default name if not provided)
     *   - courseShortName: the short name (will generate default name if not provided)
     *   - start: the start date of the course (default: now() - 2 hours)
     *   - end: the end date of the course (default: now() + 2 hours)
     *   - iconFileName: the course icon file name (default: undefined)
     *   - iconFile: the course icon file blob (default: undefined)
     *   - allowCommunication: if communication should be enabled for the course
     *   - allowMessaging: if messaging should be enabled for the course
     * @returns <Chainable> request response
     */
    createCourse(
        options: {
            customizeGroups?: boolean;
            courseName?: string;
            courseShortName?: string;
            start?: dayjs.Dayjs;
            end?: dayjs.Dayjs;
            iconFileName?: string;
            iconFile?: Blob;
            allowCommunication?: boolean;
            allowMessaging?: boolean;
        } = {},
    ): Cypress.Chainable<Cypress.Response<Course>> {
        const {
            customizeGroups = false,
            courseName = 'Course ' + generateUUID(),
            courseShortName = 'cypress' + generateUUID(),
            start = dayjs().subtract(2, 'hours'),
            end = dayjs().add(2, 'hours'),
            iconFileName,
            iconFile,
            allowCommunication = true,
            allowMessaging = true,
        } = options;

        const course = new Course();
        course.title = courseName;
        course.shortName = courseShortName;
        course.testCourse = true;
        course.startDate = start;
        course.endDate = end;

        if (allowCommunication && allowMessaging) {
            course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING;
            course.courseInformationSharingMessagingCodeOfConduct = 'Code of Conduct';
        } else if (allowCommunication) {
            course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_ONLY;
        } else if (allowMessaging) {
            course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.MESSAGING_ONLY;
            course.courseInformationSharingMessagingCodeOfConduct = 'Code of Conduct';
        } else {
            course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.DISABLED;
        }

        const allowGroupCustomization: boolean = Cypress.env('allowGroupCustomization');
        if (customizeGroups && allowGroupCustomization) {
            course.studentGroupName = Cypress.env('studentGroupName');
            course.teachingAssistantGroupName = Cypress.env('tutorGroupName');
            course.editorGroupName = Cypress.env('editorGroupName');
            course.instructorGroupName = Cypress.env('instructorGroupName');
        }

        const formData = new FormData();
        formData.append('course', new File([JSON.stringify(course)], 'course', { type: 'application/json' }));

        if (iconFile) {
            formData.append('file', iconFile, iconFileName);
        }

        return cy.request({
            url: COURSE_ADMIN_BASE,
            method: POST,
            body: formData,
        });
    }

    /**
     * Deletes the course with the specified id.
     *
     * @param course the course
     * @param admin the admin user
     * @returns A Cypress.Chainable<Cypress.Response<any>> representing the API request response.
     */
    deleteCourse(course: Course, admin: CypressCredentials) {
        if (course) {
            cy.login(admin);
            // Sometimes the server fails with a ConstraintViolationError if we delete the course immediately after a login
            cy.wait(500);
            return cy.request({ method: DELETE, url: `${COURSE_ADMIN_BASE}/${course.id}`, retryOnStatusCodeFailure: true });
        }
    }

    /**
     * Adds the specified student to the course.
     *
     * @param course - The course to which the student will be added.
     * @param user - The user (student) to be added to the course.
     * @returns A Cypress.Chainable<Cypress.Response<any>> representing the API request response.
     */
    addStudentToCourse(course: Course, user: CypressCredentials) {
        return this.addUserToCourse(course.id!, user.username, 'students');
    }

    /**
     * Adds the specified user to the tutor group in the course.
     *
     * @param course - The course to which the tutor will be added.
     * @param user - The user (tutor) to be added to the course.
     * @returns A Cypress.Chainable<Cypress.Response<any>> representing the API request response.
     */
    addTutorToCourse(course: Course, user: CypressCredentials) {
        return this.addUserToCourse(course.id!, user.username, 'tutors');
    }

    /**
     * Adds the specified user to the instructor group in the course.
     *
     * @param course - The course to which the instructor will be added.
     * @param user - The user (instructor) to be added to the course.
     * @returns A Cypress.Chainable<Cypress.Response<any>> representing the API request response.
     */
    addInstructorToCourse(course: Course, user: CypressCredentials) {
        return this.addUserToCourse(course.id!, user.username, 'instructors');
    }

    private addUserToCourse(courseId: number, username: string, roleIdentifier: string) {
        return cy.request({ method: POST, url: `${COURSE_BASE}/${courseId}/${roleIdentifier}/${username}` });
    }

    /**
     * Deletes a lecture with the specified lecture ID.
     *
     * @param lectureId - The ID of the lecture to be deleted.
     * @returns A Cypress.Chainable<Cypress.Response<any>> representing the API request response.
     */
    deleteLecture(lectureId: number) {
        return cy.request({
            url: `${BASE_API}/lectures/${lectureId}`,
            method: DELETE,
        });
    }

    /**
     * Creates a new lecture for the specified course with various options.
     *
     * @param course - The course to which the lecture belongs.
     * @param title - The title of the lecture (optional, default: auto-generated).
     * @param startDate - The start date and time of the lecture (optional, default: current date and time).
     * @param endDate - The end date and time of the lecture (optional, default: current date and time + 10 minutes).
     * @returns A Cypress.Chainable<Cypress.Response<any>> representing the API request response.
     */
    createLecture(course: Course, title = 'Lecture ' + generateUUID(), startDate = dayjs(), endDate = dayjs().add(10, 'minutes')) {
        const body = {
            ...lectureTemplate,
            course,
            title,
            startDate,
            endDate,
            channelName: 'lecture-' + titleLowercase(title),
        };
        return cy.request({
            url: `${BASE_API}/lectures`,
            method: POST,
            body,
        });
    }
}
