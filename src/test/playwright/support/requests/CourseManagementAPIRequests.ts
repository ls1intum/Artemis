import { Page } from '@playwright/test';
import dayjs from 'dayjs';

import { Course, CourseInformationSharingConfiguration } from 'app/entities/course.model';
import { Lecture } from 'app/entities/lecture.model';
import { generateUUID, titleLowercase } from '../utils';
import lectureTemplate from '../../fixtures/lecture/template.json';
import { BASE_API, COURSE_ADMIN_BASE, COURSE_BASE, Exercise } from '../constants';
import { UserCredentials } from '../users';
import { Commands } from '../commands';
import { Exam } from 'app/entities/exam.model';

/**
 * A class which encapsulates all API requests related to course management.
 */
export class CourseManagementAPIRequests {
    private readonly page: Page;

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
     * @returns Promise<Course> representing the course created
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
            courseShortName = 'playwright' + generateUUID(),
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

        const allowGroupCustomization: boolean = process.env.ALLOW_GROUP_CUSTOMIZATION === 'true';
        if (customizeGroups && allowGroupCustomization) {
            course.studentGroupName = process.env.STUDENT_GROUP_NAME;
            course.teachingAssistantGroupName = process.env.TUTOR_GROUP_NAME;
            course.editorGroupName = process.env.EDITOR_GROUP_NAME;
            course.instructorGroupName = process.env.INSTRUCTOR_GROUP_NAME;
        }

        const iconBuffer = await new Response(iconFile).arrayBuffer();

        const multipart = {
            course: {
                name: 'course',
                mimeType: 'application/json',
                buffer: Buffer.from(JSON.stringify(course)),
            },
        };

        if (iconFileName) {
            multipart['file'] = {
                name: iconFileName,
                mimeType: 'application/octet-stream',
                buffer: Buffer.from(iconBuffer),
            };
        }

        const response = await this.page.request.post(COURSE_ADMIN_BASE, { multipart });
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

            // Retry in case of failures (with timeout in ms.)
            const timeout = 5000;
            const startTime = Date.now();
            while (Date.now() - startTime < timeout) {
                const response = await this.page.request.delete(`${COURSE_ADMIN_BASE}/${course.id}`);
                if (response.ok()) {
                    break;
                }
                console.log('Retrying delete course request due to failure');
                await this.page.waitForTimeout(500);
            }
        }
    }

    /**
     * Adds the specified student to the course.
     *
     * @param course - The course to which the student will be added.
     * @param user - The user (student) to be added to the course.
     */
    async addStudentToCourse(course: Course, user: UserCredentials) {
        await this.addUserToCourse(course.id!, user.username, 'students');
    }

    /**
     * Adds the specified user to the tutor group in the course.
     *
     * @param course - The course to which the tutor will be added.
     * @param user - The user (tutor) to be added to the course.
     */
    async addTutorToCourse(course: Course, user: UserCredentials) {
        await this.addUserToCourse(course.id!, user.username, 'tutors');
    }

    /**
     * Adds the specified user to the instructor group in the course.
     *
     * @param course - The course to which the instructor will be added.
     * @param user - The user (instructor) to be added to the course.
     */
    async addInstructorToCourse(course: Course, user: UserCredentials) {
        await this.addUserToCourse(course.id!, user.username, 'instructors');
    }

    private async addUserToCourse(courseId: number, username: string, roleIdentifier: string) {
        await this.page.request.post(`${COURSE_BASE}/${courseId}/${roleIdentifier}/${username}`);
    }

    /**
     * Creates a new lecture for the specified course with various options.
     *
     * @param course - The course to which the lecture belongs.
     * @param title - The title of the lecture (optional, default: auto-generated).
     * @param startDate - The start date and time of the lecture (optional, default: current date and time).
     * @param endDate - The end date and time of the lecture (optional, default: current date and time + 10 minutes).
     * @returns Promise<Lecture> representing the lecture created.
     */
    async createLecture(course: Course, title = 'Lecture ' + generateUUID(), startDate = dayjs(), endDate = dayjs().add(10, 'minutes')): Promise<Lecture> {
        const data = {
            ...lectureTemplate,
            course,
            title,
            startDate,
            endDate,
            channelName: 'lecture-' + titleLowercase(title),
        };
        const response = await this.page.request.post(`${BASE_API}/lectures`, { data });
        return response.json();
    }

    /**
     * Deletes a lecture with the specified lecture ID.
     *
     * @param lectureId - The ID of the lecture to be deleted.
     */
    async deleteLecture(lectureId: number) {
        await this.page.request.delete(`${BASE_API}/lectures/${lectureId}`);
    }

    async createExamTestRun(exam: Exam, exercises: Array<Exercise>) {
        const data = {
            workingTime: 120,
            exam,
            exercises,
            ended: false,
            numberOfExamSessions: 0,
        };
        const response = await this.page.request.post(`${COURSE_BASE}/${exam.course!.id}/exams/${exam.id}/test-run`, { data });
        return response.json();
    }
}
