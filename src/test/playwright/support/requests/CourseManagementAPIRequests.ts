import { Page } from '@playwright/test';
import dayjs from 'dayjs';

import { Course, CourseInformationSharingConfiguration } from 'app/course/shared/entities/course.model';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { asModelDate, generateUUID, titleLowercase } from '../utils';
import lectureTemplate from '../../fixtures/lecture/template.json';
import { COURSE_ADMIN_BASE, Exercise } from '../constants';
import { UserCredentials } from '../users';
import { Commands } from '../commands';
import { Exam } from 'app/exam/shared/entities/exam.model';

/**
 * Mirrors `getCurrentSemester` from `app/foundation/util/semester-utils`, generalised to any date. That helper
 * cannot be imported here: it calls `dayjs()` as a runtime value (not just as a type), which forces a real load of
 * the `dayjs/esm` build. That build's own internal `import * as C from './constant'` (no `.js` extension) is not
 * resolvable under the strict native ESM resolution that Playwright's test-discovery pass uses, and breaks
 * collection for the whole suite. Keep this in sync with `getCurrentSemester`'s WS/SS boundary rule (October to
 * March is winter).
 *
 * It takes the date rather than always reading the clock so that a course created with custom dates gets the
 * semester those dates fall in. Passing today's date reproduces `getCurrentSemester` exactly.
 */
function semesterOf(date: dayjs.Dayjs): string {
    const month = date.month(); // 0-indexed (0 = January)
    const yearShort = date.year() - 2000;

    if (month >= 9) {
        return `WS${yearShort}/${yearShort + 1}`;
    } else if (month <= 2) {
        return `WS${yearShort - 1}/${yearShort}`;
    } else {
        return `SS${yearShort}`;
    }
}

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
     *   - courseName: the title of the course (will generate default name if not provided)
     *   - courseShortName: the short name (will generate default name if not provided)
     *   - start: the start date of the course (default: now() - 2 hours)
     *   - end: the end date of the course (default: now() + 2 hours)
     *   - semester: the semester of the course (default: the semester the start date falls in, so a custom start stays consistent with it)
     *   - iconFileName: the course icon file name (default: undefined)
     *   - iconFile: the course icon file blob (default: undefined)
     *   - allowCommunication: if communication should be enabled for the course
     *   - allowMessaging: if messaging should be enabled for the course
     *   - timeZone: the IANA time zone of the course (default: undefined; required before tutorial groups can be added)
     * @returns Promise<Course> representing the course created
     */
    async createCourse(
        options: {
            courseName?: string;
            courseShortName?: string;
            start?: dayjs.Dayjs;
            end?: dayjs.Dayjs;
            semester?: string;
            iconFileName?: string;
            iconFile?: Blob;
            allowCommunication?: boolean;
            allowMessaging?: boolean;
            timeZone?: string;
        } = {},
    ): Promise<Course> {
        const {
            courseName = 'Course ' + generateUUID(),
            courseShortName = 'playwright' + generateUUID(),
            start = dayjs().subtract(2, 'hours'),
            end = dayjs().add(2, 'hours'),
            // Derived from the start date, which is already bound above: a caller that shifts the course into another
            // semester should not have to restate the semester to keep the two consistent.
            semester = semesterOf(start),
            iconFileName,
            iconFile,
            allowCommunication = true,
            allowMessaging = true,
            timeZone,
        } = options;

        const course = new Course();
        course.title = courseName;
        course.shortName = courseShortName;
        course.testCourse = true;
        course.startDate = asModelDate(start);
        course.endDate = asModelDate(end);
        course.semester = semester;
        course.timeZone = timeZone;

        if (allowCommunication && allowMessaging) {
            course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING;
            course.courseInformationSharingMessagingCodeOfConduct = 'Code of Conduct';
        } else if (allowCommunication) {
            course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_ONLY;
        } else {
            course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.DISABLED;
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
            // @ts-expect-error: dynamically adding file part to multipart form data
            multipart['file'] = {
                name: iconFileName,
                mimeType: 'application/octet-stream',
                buffer: Buffer.from(iconBuffer),
            };
        }

        const response = await this.page.request.post(COURSE_ADMIN_BASE, { multipart });
        if (!response.ok()) {
            throw new Error(`Failed to create course: ${response.status()} ${response.statusText()} - ${await response.text()}`);
        }
        return response.json();
    }

    /**
     * Deletes the course with the specified id.
     *
     * Treats 404 as success: the course is already gone (typically because a previous test or
     * fixture already deleted it), which is the desired post-condition for teardown. Other
     * non-2xx statuses are retried briefly to absorb transient infrastructure noise. If the
     * retry budget is exhausted without a 2xx or 404, throws so the teardown failure surfaces
     * instead of being silently swallowed.
     *
     * @param course the course (may be undefined; call is a no-op in that case so callers can
     *               unconditionally clean up describe-scoped variables without checking themselves)
     * @param admin the admin user
     */
    async deleteCourse(course: Course | undefined, admin: UserCredentials) {
        if (!course) {
            return;
        }
        await Commands.login(this.page, admin);

        // Retry briefly on transient failures (e.g. CI infra hiccups). 5xx and other non-2xx,
        // non-404 statuses retry; 404 short-circuits because "already gone" == cleanup done.
        const timeout = 5000;
        const startTime = Date.now();
        let lastStatus: number | undefined;
        let lastUrl: string | undefined;
        while (Date.now() - startTime < timeout) {
            const response = await this.page.request.delete(`${COURSE_ADMIN_BASE}/${course.id}`);
            if (response.ok() || response.status() === 404) {
                return;
            }
            lastStatus = response.status();
            lastUrl = response.url();
            console.log(`Retrying delete course request: status=${lastStatus} url=${lastUrl}`);
            await this.page.waitForTimeout(500);
        }
        throw new Error(`deleteCourse timed out after ${timeout}ms (last status=${lastStatus} url=${lastUrl}) — course id=${course.id} was not deleted`);
    }

    /**
     * Updates the maxComplaints setting for a course via API.
     * Useful for tests that file complaints on shared seed courses.
     */
    async updateCourseMaxComplaints(courseId: number, maxComplaints: number) {
        const courseResponse = await this.page.request.get(`api/course/courses/${courseId}`);
        const courseData = await courseResponse.json();
        courseData.maxComplaints = maxComplaints;
        const response = await this.page.request.put(`api/course/courses/${courseId}`, {
            multipart: {
                course: {
                    name: 'course',
                    mimeType: 'application/json',
                    buffer: Buffer.from(JSON.stringify(courseData)),
                },
            },
        });
        return response;
    }

    /**
     * Moves the end date of a course into the past.
     *
     * Archiving is refused while a course is still running, so a test that has to participate first and archive
     * afterwards cannot simply create the course as finished.
     *
     * @param courseId the course to move
     * @param end      the new end date, by default one hour ago
     */
    async setCourseEndDate(courseId: number, end: dayjs.Dayjs = dayjs().subtract(1, 'hour')) {
        const courseResponse = await this.page.request.get(`api/course/courses/${courseId}`);
        const courseData = await courseResponse.json();
        courseData.endDate = end.toISOString();
        const response = await this.page.request.put(`api/course/courses/${courseId}`, {
            multipart: {
                course: {
                    name: 'course',
                    mimeType: 'application/json',
                    buffer: Buffer.from(JSON.stringify(courseData)),
                },
            },
        });
        if (!response.ok()) {
            throw new Error(`Could not move the end date of course ${courseId}: ${response.status()} ${await response.text()}`);
        }
        return response;
    }

    /**
     * Waits until the archive of a course can be downloaded.
     *
     * Archiving runs asynchronously, and the only externally visible signal that it finished is that the download
     * endpoint stops answering 404, so that is what is polled.
     *
     * @param courseId the archived course
     * @param timeout  how long to wait in milliseconds
     */
    async waitForCourseArchive(courseId: number, timeout = 120000) {
        const startTime = Date.now();
        let lastStatus = 0;
        while (Date.now() - startTime < timeout) {
            const response = await this.page.request.get(`api/course/courses/${courseId}/download-archive`);
            if (response.ok()) {
                return;
            }
            lastStatus = response.status();
            await this.page.waitForTimeout(2000);
        }
        throw new Error(`The archive of course ${courseId} was not ready within ${timeout}ms (last status ${lastStatus})`);
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
        await this.page.request.post(`api/course/courses/${courseId}/${roleIdentifier}/${username}`);
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

    /**
     * Creates an accepted FAQ, which is the only state students can see.
     *
     * @param course - The course the FAQ belongs to.
     * @param questionTitle - The title of the FAQ.
     * @param questionAnswer - The answer text (optional, default: a generic answer).
     * @returns Promise<{ id: number; questionTitle: string }> the created FAQ.
     */
    async createFaq(course: Course, questionTitle: string, questionAnswer = 'The answer to the question.'): Promise<{ id: number; questionTitle: string }> {
        const data = { courseId: course.id, questionTitle, questionAnswer, faqState: 'ACCEPTED', categories: [] };
        const response = await this.page.request.post(`api/communication/courses/${course.id}/faqs`, { data });
        if (!response.ok()) {
            throw new Error(`Failed to create FAQ: ${response.status()} ${await response.text()}`);
        }
        return response.json();
    }

    /**
     * Creates the tutorial groups configuration of a course, which has to exist before any tutorial group can be added.
     * The course must have been created with a time zone, otherwise the server rejects the configuration.
     *
     * @param course - The course to configure.
     */
    async createTutorialGroupsConfiguration(course: Course) {
        const data = {
            tutorialPeriodStartInclusive: dayjs().subtract(1, 'year').format('YYYY-MM-DD'),
            tutorialPeriodEndInclusive: dayjs().add(1, 'year').format('YYYY-MM-DD'),
            useTutorialGroupChannels: false,
            usePublicTutorialGroupChannels: false,
        };
        const response = await this.page.request.post(`api/tutorialgroup/courses/${course.id}/tutorial-groups-configuration`, { data });
        if (!response.ok()) {
            throw new Error(`Failed to create tutorial groups configuration: ${response.status()} ${await response.text()}`);
        }
    }

    /**
     * Creates a tutorial group taught by the given tutor. Requires {@link createTutorialGroupsConfiguration} to have run.
     *
     * @param course - The course the tutorial group belongs to.
     * @param title - The title of the tutorial group. The server only accepts alphanumerics, spaces, colons and dashes, max 20 characters.
     * @param tutorId - The id of the tutor teaching the group.
     * @returns Promise<number> the id of the created tutorial group.
     */
    async createTutorialGroup(course: Course, title: string, tutorId: number): Promise<number> {
        const data = { title, tutorId, language: 'ENGLISH', isOnline: false, campus: 'Garching', capacity: 10 };
        const response = await this.page.request.post(`api/tutorialgroup/courses/${course.id}/tutorial-groups`, { data });
        if (!response.ok()) {
            throw new Error(`Failed to create tutorial group: ${response.status()} ${await response.text()}`);
        }
        return response.json();
    }

    /**
     * Registers students in a tutorial group. A student who is registered somewhere sees their groups in the expanded
     * "my groups" section of the tutorial groups sidebar, which is where a real student finds them.
     *
     * @param course - The course the tutorial group belongs to.
     * @param tutorialGroupId - The id of the tutorial group.
     * @param users - The students to register.
     */
    async registerStudentsInTutorialGroup(course: Course, tutorialGroupId: number, users: UserCredentials[]) {
        const data = users.map((user) => user.username);
        const response = await this.page.request.post(`api/tutorialgroup/courses/${course.id}/tutorial-groups/${tutorialGroupId}/batch-register`, { data });
        if (!response.ok()) {
            throw new Error(`Failed to register students in tutorial group: ${response.status()} ${await response.text()}`);
        }
    }

    async createExamTestRun(exam: Exam, exercises: Array<Exercise>) {
        // 1080s (18 min) matches the previous default here. The old 120s budget routinely
        // expired mid-test under heavy parallel multi-node load — four sequential exercise
        // submissions (TEXT + PROGRAMMING + QUIZ + MODELING) plus the navigation between them
        // can easily exceed two minutes when the cluster is busy, causing the exam clock to
        // hit zero before the test finishes submitting and dropping the page on the
        // end-of-exam screen.
        // Flat CreateTestRunDTO(examId, exerciseIds, workingTime) — matches the server's
        // request shape post-DTO-migration (StudentExamResource#createTestRun); the server
        // never reads exam/exercise objects wholesale, only examId + exercise ids + workingTime.
        const data = {
            examId: exam.id,
            exerciseIds: exercises.map((exercise) => exercise.id),
            workingTime: 1080,
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
     * @param taxonomy - The Bloom's taxonomy level of the competency (optional).
     * @returns Promise with the created competency.
     */
    async createCompetency(course: Course, title: string, description?: string, taxonomy?: 'REMEMBER' | 'UNDERSTAND' | 'APPLY' | 'ANALYZE' | 'EVALUATE' | 'CREATE') {
        const data = {
            type: 'competency',
            title,
            description: description || `Description for ${title}`,
            masteryThreshold: 100,
            taxonomy,
        };
        const response = await this.page.request.post(`api/atlas/courses/${course.id}/competencies`, { data });
        if (!response.ok()) {
            const errorBody = await response.text();
            throw new Error(`Failed to create competency: ${response.status()} ${response.statusText()} - ${errorBody}`);
        }
        return response.json();
    }

    /**
     * Deletes a competency from the specified course via API.
     *
     * @param course - The course to which the competency belongs.
     * @param competencyId - The id of the competency to delete.
     */
    async deleteCompetency(course: Course, competencyId: number) {
        const response = await this.page.request.delete(`api/atlas/courses/${course.id}/competencies/${competencyId}`);
        if (!response.ok()) {
            const errorBody = await response.text();
            throw new Error(`Failed to delete competency: ${response.status()} ${response.statusText()} - ${errorBody}`);
        }
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
