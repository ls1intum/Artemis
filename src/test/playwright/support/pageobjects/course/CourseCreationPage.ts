import { Page } from '@playwright/test';
import dayjs from 'dayjs';

import { BASE_API } from '../../constants';
import { enterDate } from '../../utils';

export class CourseCreationPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async setTitle(title: string) {
        await this.page.locator('#field_title').fill(title);
    }

    async setShortName(shortName: string) {
        await this.page.locator('#field_shortName').fill(shortName);
    }

    async setDescription(description: string) {
        await this.page.locator('#field_description').fill(description);
    }

    async setStartDate(date: dayjs.Dayjs) {
        await enterDate(this.page, '#field_startDate', date);
    }

    async setEndDate(date: dayjs.Dayjs) {
        await enterDate(this.page, '#field_endDate', date);
    }

    async setTestCourse(testCourse: boolean) {
        const selector = this.page.locator('#field_testCourse');
        if (testCourse) {
            await selector.check();
        } else {
            await selector.uncheck();
        }
    }

    async setSemester(semester: string) {
        await this.page.locator('#semester').selectOption(semester);
    }

    async setCourseMaxPoints(courseMaxPoints: number) {
        await this.page.locator('#field_maxPoints').fill(courseMaxPoints.toString());
    }

    async setProgrammingLanguage(programmingLanguage: string) {
        await this.page.locator('#programmingLanguage').selectOption(programmingLanguage);
    }

    async setCustomizeGroupNames(customizeGroupNames: boolean) {
        const selector = this.page.locator('#field_customizeGroupNamesEnabled');
        if (customizeGroupNames) {
            await selector.check();
        } else {
            await selector.uncheck();
        }
    }

    async setStudentGroup(groupName: string) {
        await this.page.locator('#field_studentGroupName').fill(groupName);
    }

    async setTutorGroup(groupName: string) {
        await this.page.locator('#field_teachingAssistantGroupName').fill(groupName);
    }

    async setEditorGroup(groupName: string) {
        await this.page.locator('#field_editorGroupName').fill(groupName);
    }

    async setInstructorGroup(groupName: string) {
        await this.page.locator('#field_instructorGroupName').fill(groupName);
    }

    async setEnableComplaints(complaints: boolean) {
        const selector = this.page.locator('#field_maxComplaintSettingEnabled');
        if (complaints) {
            await selector.check();
        } else {
            await selector.uncheck();
        }
    }

    async setMaxComplaints(maxComplaints: number) {
        await this.page.locator('#field_maxComplaints').fill(maxComplaints.toString());
    }

    async setMaxTeamComplaints(maxTeamComplaints: number) {
        await this.page.locator('#field_maxTeamComplaints').fill(maxTeamComplaints.toString());
    }

    async setMaxComplaintsTimeDays(maxComplaintsTimeDays: number) {
        await this.page.locator('#field_maxComplaintTimeDays').fill(maxComplaintsTimeDays.toString());
    }

    async setMaxComplaintTextLimit(maxComplaintTextLimit: number) {
        await this.page.locator('#field_maxComplaintTextLimit').fill(maxComplaintTextLimit.toString());
    }

    async setMaxComplaintResponseTextLimit(maxComplaintResponseTextLimit: number) {
        await this.page.locator('#field_maxComplaintResponseTextLimit').fill(maxComplaintResponseTextLimit.toString());
    }

    async setEnableMoreFeedback(moreFeedback: boolean) {
        const selector = this.page.locator('#field_maxRequestMoreFeedbackSettingEnabled');
        if (moreFeedback) {
            await selector.check();
        } else {
            await selector.uncheck();
        }
    }

    async setMaxRequestMoreFeedbackTimeDays(maxRequestMoreFeedbackTimeDays: number) {
        await this.page.locator('#field_maxRequestMoreFeedbackTimeDays').fill(maxRequestMoreFeedbackTimeDays.toString());
    }

    async setOnlineCourse(onlineCourse: boolean) {
        const selector = this.page.locator('#field_onlineCourse');
        if (onlineCourse) {
            await selector.check();
        } else {
            await selector.uncheck();
        }
    }

    async setRegistrationEnabled(registration: boolean) {
        const selector = this.page.locator('#field_registrationEnabled');
        if (registration) {
            await selector.check();
        } else {
            await selector.uncheck();
        }
    }

    async submit() {
        const responsePromise = this.page.waitForResponse(BASE_API + 'admin/courses');
        await this.page.click('#save-entity');
        const response = await responsePromise;
        return await response.json();
    }

    async update() {
        const responsePromise = this.page.waitForResponse(BASE_API + 'courses/*');
        await this.page.click('#save-entity');
        const response = await responsePromise;
        return response.json();
    }
}
