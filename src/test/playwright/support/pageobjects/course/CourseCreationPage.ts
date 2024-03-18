import { Page } from '@playwright/test';
import dayjs from 'dayjs';

import { COURSE_ADMIN_BASE, COURSE_BASE } from '../../constants';
import { enterDate } from '../../utils';

/**
 * A class which encapsulates UI selectors and actions for the course creation page.
 */
export class CourseCreationPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    /**
     * Sets the title of the course.
     * @param title the exam title
     */
    async setTitle(title: string) {
        await this.page.locator('#field_title').fill(title);
    }

    /**
     * Sets the short name of the course.
     * @param shortName the course short name
     */
    async setShortName(shortName: string) {
        await this.page.locator('#field_shortName').fill(shortName);
    }

    /**
     * Sets the description of the course.
     * @param description the course description
     */
    async setDescription(description: string) {
        await this.page.locator('#field_description').fill(description);
    }

    /**
     * @param date the date when the course starts
     */
    async setStartDate(date: dayjs.Dayjs) {
        await enterDate(this.page, '#field_startDate', date);
    }

    /**
     * @param date the date when the course will end
     */
    async setEndDate(date: dayjs.Dayjs) {
        await enterDate(this.page, '#field_endDate', date);
    }

    /**
     * Sets course to be a test course
     * @param testCourse if is a test course
     */
    async setTestCourse(testCourse: boolean) {
        const selector = this.page.locator('#field_testCourse');
        if (testCourse) {
            await selector.check();
        } else {
            await selector.uncheck();
        }
    }

    /**
     * Sets semester for the course
     * @param semester the semester of the course
     */
    async setSemester(semester: string) {
        await this.page.locator('#semester').selectOption(semester);
    }

    /**
     * Sets the maximum achievable points in the course.
     * @param courseMaxPoints the max points
     */
    async setCourseMaxPoints(courseMaxPoints: number) {
        await this.page.locator('#field_maxPoints').fill(courseMaxPoints.toString());
    }

    /**
     * Sets the default programming language
     * @param programmingLanguage the programming language
     */
    async setProgrammingLanguage(programmingLanguage: string) {
        await this.page.locator('#programmingLanguage').selectOption(programmingLanguage);
    }

    /**
     * Sets if the course group names should be customized
     * @param customizeGroupNames customize the group names
     */
    async setCustomizeGroupNames(customizeGroupNames: boolean) {
        const selector = this.page.locator('#field_customizeGroupNamesEnabled');
        if (customizeGroupNames) {
            await selector.check();
        } else {
            await selector.uncheck();
        }
    }

    /**
     * Sets the customized group name for students
     * @param groupName the group name
     */
    async setStudentGroup(groupName: string) {
        await this.page.locator('#field_studentGroupName').fill(groupName);
    }

    /**
     * Sets the customized group name for tutors
     * @param groupName the group name
     */
    async setTutorGroup(groupName: string) {
        await this.page.locator('#field_teachingAssistantGroupName').fill(groupName);
    }

    /**
     * Sets the customized group name for editors
     * @param groupName the group name
     */
    async setEditorGroup(groupName: string) {
        await this.page.locator('#field_editorGroupName').fill(groupName);
    }

    /**
     * Sets the customized group name for instructors
     * @param groupName the group name
     */
    async setInstructorGroup(groupName: string) {
        await this.page.locator('#field_instructorGroupName').fill(groupName);
    }

    /**
     * Sets if complaints are enabled
     * @param complaints if complaints should be enabled
     */
    async setEnableComplaints(complaints: boolean) {
        const selector = this.page.locator('#field_maxComplaintSettingEnabled');
        if (complaints) {
            await selector.check();
        } else {
            await selector.uncheck();
        }
    }

    /**
     * Sets maximum amount of complaints
     * @param maxComplaints the maximum complaints
     */
    async setMaxComplaints(maxComplaints: number) {
        await this.page.locator('#field_maxComplaints').fill(maxComplaints.toString());
    }

    /**
     * Sets maximum amount of team complaints
     * @param maxTeamComplaints the maximum team complaints
     */
    async setMaxTeamComplaints(maxTeamComplaints: number) {
        await this.page.locator('#field_maxTeamComplaints').fill(maxTeamComplaints.toString());
    }

    /**
     * Sets the maximal complaints time in days
     * @param maxComplaintsTimeDays the maximal complaints time in days
     */
    async setMaxComplaintsTimeDays(maxComplaintsTimeDays: number) {
        await this.page.locator('#field_maxComplaintTimeDays').fill(maxComplaintsTimeDays.toString());
    }

    /**
     * Sets the maximal complaint text limit
     * @param maxComplaintTextLimit the maximal complaint text limit
     */
    async setMaxComplaintTextLimit(maxComplaintTextLimit: number) {
        await this.page.locator('#field_maxComplaintTextLimit').fill(maxComplaintTextLimit.toString());
    }

    /**
     * Sets the maximal complaint response text limit
     * @param maxComplaintResponseTextLimit the maximal complaint response text limit
     */
    async setMaxComplaintResponseTextLimit(maxComplaintResponseTextLimit: number) {
        await this.page.locator('#field_maxComplaintResponseTextLimit').fill(maxComplaintResponseTextLimit.toString());
    }

    /**
     * Sets if more feedback requests are enabled
     * @param moreFeedback if more feedback should be enabled
     */
    async setEnableMoreFeedback(moreFeedback: boolean) {
        const selector = this.page.locator('#field_maxRequestMoreFeedbackSettingEnabled');
        if (moreFeedback) {
            await selector.check();
        } else {
            await selector.uncheck();
        }
    }

    /**
     * Sets the maximal request more feedback time in days
     * @param maxRequestMoreFeedbackTimeDays maximal request more feedback time in days
     */
    async setMaxRequestMoreFeedbackTimeDays(maxRequestMoreFeedbackTimeDays: number) {
        await this.page.locator('#field_maxRequestMoreFeedbackTimeDays').fill(maxRequestMoreFeedbackTimeDays.toString());
    }

    /**
     * Sets if course is an online course
     * @param onlineCourse if should be online course
     */
    async setOnlineCourse(onlineCourse: boolean) {
        const selector = this.page.locator('#field_onlineCourse');
        if (onlineCourse) {
            await selector.check();
        } else {
            await selector.uncheck();
        }
    }

    /**
     * Sets if students can register for this course
     * @param registration if registration should be possible
     */
    async setRegistrationEnabled(registration: boolean) {
        const selector = this.page.locator('#field_registrationEnabled');
        if (registration) {
            await selector.check();
        } else {
            await selector.uncheck();
        }
    }

    /**
     * Submits the created exam.
     * @returns the response if a test needs it
     */
    async submit() {
        const responsePromise = this.page.waitForResponse(COURSE_ADMIN_BASE);
        await this.page.click('#save-entity');
        const response = await responsePromise;
        return await response.json();
    }

    /**
     * Updates the created exam.
     * @returns the response if a test needs it
     */
    async update() {
        const responsePromise = this.page.waitForResponse(`${COURSE_BASE}/*`);
        await this.page.click('#save-entity');
        const response = await responsePromise;
        return response.json();
    }
}
