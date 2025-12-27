import { Page } from '@playwright/test';
import dayjs from 'dayjs';

import { Course, CourseInformationSharingConfiguration } from 'app/core/course/shared/entities/course.model';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { generateUUID, titleLowercase } from '../utils';
import lectureTemplate from '../../fixtures/lecture/template.json';
import { COURSE_ADMIN_BASE, Exercise } from '../constants';
import { UserCredentials } from '../users';
import { Commands } from '../commands';
import { Exam } from 'app/exam/shared/entities/exam.model';

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
            // @ts-ignore
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
        await this.page.request.post(`api/core/courses/${courseId}/${roleIdentifier}/${username}`);
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
        const response = await this.page.request.post(`api/lecture/lectures`, { data });
        return response.json();
    }

    /**
     * Deletes a lecture with the specified lecture ID.
     *
     * @param lectureId - The ID of the lecture to be deleted.
     */
    async deleteLecture(lectureId: number) {
        await this.page.request.delete(`api/lecture/lectures/${lectureId}`);
    }

    async createExamTestRun(exam: Exam, exercises: Array<Exercise>) {
        const data = {
            workingTime: 120,
            exam,
            exercises,
            ended: false,
            numberOfExamSessions: 0,
        };
        const response = await this.page.request.post(`api/exam/courses/${exam.course!.id}/exams/${exam.id}/test-run`, { data });
        return response.json();
    }

    /**
     * Creates a competency for the specified course via API.
     *
     * @param course - The course to which the competency belongs.
     * @param title - The title of the competency.
     * @param description - The description of the competency (optional).
     * @returns Promise with the created competency.
     */
    async createCompetency(course: Course, title: string, description?: string) {
        const data = {
            type: 'competency',
            title,
            description: description || `Description for ${title}`,
            masteryThreshold: 100,
        };
        const response = await this.page.request.post(`api/atlas/courses/${course.id}/competencies`, { data });
        if (!response.ok()) {
            const errorBody = await response.text();
            throw new Error(`Failed to create competency: ${response.status()} ${response.statusText()} - ${errorBody}`);
        }
        return response.json();
    }

    /**
     * Creates a prerequisite for the specified course via API.
     *
     * @param course - The course to which the prerequisite belongs.
     * @param title - The title of the prerequisite.
     * @param description - The description of the prerequisite (optional).
     * @returns Promise with the created prerequisite.
     */
    async createPrerequisite(course: Course, title: string, description?: string) {
        const data = {
            type: 'prerequisite',
            title,
            description: description || `Description for ${title}`,
            masteryThreshold: 100,
        };
        const response = await this.page.request.post(`api/atlas/courses/${course.id}/prerequisites`, { data });
        if (!response.ok()) {
            const errorBody = await response.text();
            throw new Error(`Failed to create prerequisite: ${response.status()} ${response.statusText()} - ${errorBody}`);
        }
        return response.json();
    }

    /**
     * Creates a competency relation for the specified course via API.
     *
     * @param course - The course to which the competencies belong.
     * @param tailCompetencyId - The ID of the tail competency.
     * @param headCompetencyId - The ID of the head competency.
     * @param relationType - The type of relation ('ASSUMES', 'EXTENDS', or 'MATCHES').
     * @returns Promise with the created relation.
     */
    async createCompetencyRelation(course: Course, tailCompetencyId: number, headCompetencyId: number, relationType: 'ASSUMES' | 'EXTENDS' | 'MATCHES') {
        const data = {
            tailCompetencyId,
            headCompetencyId,
            relationType,
        };
        const response = await this.page.request.post(`api/atlas/courses/${course.id}/course-competencies/relations`, { data });
        if (!response.ok()) {
            const errorBody = await response.text();
            throw new Error(`Failed to create competency relation: ${response.status()} ${response.statusText()} - ${errorBody}`);
        }
        return response.json();
    }

    /**
     * Creates a text unit for the specified lecture via API.
     *
     * @param lecture - The lecture to which the text unit belongs.
     * @param name - The name of the text unit.
     * @param content - The content of the text unit (optional).
     * @param competencyLinks - Optional array of competency links to associate with the text unit.
     *                          Each link should have { competency: { id, type }, weight }.
     *                          The type should be 'competency' or 'prerequisite' for Jackson polymorphic deserialization.
     * @returns Promise with the created text unit.
     */
    async createTextUnit(
        lecture: Lecture,
        name: string,
        content?: string,
        competencyLinks?: Array<{ competency: { id: number; type: string }; weight: number }>,
    ): Promise<{ id: number; name: string; content: string; type: string }> {
        const data: {
            type: string;
            name: string;
            content: string;
            releaseDate: string;
            competencyLinks?: Array<{ competency: { id: number; type: string }; weight: number }>;
        } = {
            type: 'text',
            name,
            content: content || `Content for ${name}`,
            releaseDate: dayjs().subtract(1, 'hour').toISOString(),
        };
        if (competencyLinks && competencyLinks.length > 0) {
            data.competencyLinks = competencyLinks;
        }
        const response = await this.page.request.post(`api/lecture/lectures/${lecture.id}/text-units`, { data });
        if (!response.ok()) {
            const errorBody = await response.text();
            throw new Error(`Failed to create text unit: ${response.status()} ${response.statusText()} - ${errorBody}`);
        }
        return response.json();
    }

    /**
     * Enables learning paths for the specified course via API.
     *
     * @param course - The course for which learning paths should be enabled.
     * @returns Promise that resolves when learning paths are enabled.
     */
    async enableLearningPaths(course: Course): Promise<void> {
        const response = await this.page.request.put(`api/atlas/courses/${course.id}/learning-paths/enable`);
        if (!response.ok()) {
            const errorBody = await response.text();
            throw new Error(`Failed to enable learning paths: ${response.status()} ${response.statusText()} - ${errorBody}`);
        }
    }
}
