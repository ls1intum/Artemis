import dayjs from 'dayjs';
import { generateUUID } from '../utils';
import { Course, CourseInformationSharingConfiguration } from 'app/entities/course.model';
import { Page } from '@playwright/test';
import { COURSE_ADMIN_BASE } from '../constants';
import { UserCredentials } from '../users';
import { Commands } from '../commands';

/**
 * A class which encapsulates all API requests related to course management.
 */
export class CourseManagementAPIRequests {
    private page: Page;

    constructor(page: Page) {
        this.page = page;
    }

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
     * @returns Promise<Course> request response
     */
    async createCourse(
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
    ): Promise<Course> {
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

        const allowGroupCustomization: boolean = process.env.allowGroupCustomization === 'true';
        if (customizeGroups && allowGroupCustomization) {
            course.studentGroupName = process.env.studentGroupName;
            course.teachingAssistantGroupName = process.env.tutorGroupName;
            course.editorGroupName = process.env.editorGroupName;
            course.instructorGroupName = process.env.instructorGroupName;
        }

        const iconBuffer = await new Response(iconFile).arrayBuffer();

        const multipartData = {
            course: {
                name: 'course',
                mimeType: 'application/json',
                buffer: Buffer.from(JSON.stringify(course)),
            },
        };

        if (iconFileName) {
            multipartData['file'] = {
                name: iconFileName,
                mimeType: 'application/octet-stream',
                buffer: Buffer.from(iconBuffer),
            };
        }

        const response = await this.page.request.post(COURSE_ADMIN_BASE, {
            multipart: multipartData,
        });
        return response.json();
    }

    /**
     * Deletes the course with the specified id.
     *
     * @param course the course
     * @param admin the admin user
     */
    async deleteCourse(course: Course, admin: UserCredentials) {
        if (course) {
            await Commands.login(this.page, admin);
            // Sometimes the server fails with a ConstraintViolationError if we delete the course immediately after a login
            await this.page.waitForTimeout(500);
            // TODO: Add retry mechanism in case of failures (with timeout)
            await this.page.request.delete(`${COURSE_ADMIN_BASE}/${course.id}`);
        }
    }
}
