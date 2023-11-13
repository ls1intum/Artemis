import { Page } from '@playwright/test';
import { UserCredentials } from '../../users';
import { COURSE_BASE } from '../../constants';

/**
 * A class which encapsulates UI selectors and actions for the course management page.
 */
export class CourseManagementPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async openCourseCreation() {
        await this.page.locator('#create-course').click();
    }

    async openCourseEdit() {
        await this.page.locator('#edit-course').click();
    }

    /**
     * @returns Returns the cypress chainable containing the root element of the course card of our created course.
     * This can be used to find specific elements within this course card.
     */
    getCourse(courseID: number) {
        return this.page.locator(`#course-${courseID}`);
    }

    /**
     * Opens the students overview page of a course.
     * @param courseId the id of the course
     */
    async openStudentOverviewOfCourse(courseId: number) {
        await this.page.locator('#open-student-management-' + courseId).click();
    }

    /**
     * Opens a course.
     * @param courseID
     */
    async openCourse(courseID: number) {
        await this.getCourse(courseID).locator('#course-card-header').click();
    }

    /**
     * Adds the user to the student group of the course
     * @param credentials the user that gets added to the student group of the course
     * */
    async addStudentToCourse(credentials: UserCredentials) {
        const responsePromise = this.page.waitForResponse(COURSE_BASE + '*/students/' + credentials.username);
        await this.page.locator('#add-students').click();
        await this.confirmUserIntoGroup(credentials);
        await responsePromise;
    }

    async removeFirstUser() {
        await this.page.locator('#registered-students button[jhideletebutton]').first().click();
        await this.page.locator('.modal #delete').click();
    }

    /**
     * helper method to avoid code duplication
     * */
    private async confirmUserIntoGroup(credentials: UserCredentials) {
        await this.page.locator('#typeahead-basic').fill(credentials.username);
        await this.page.keyboard.press('Enter');
        await this.page.locator('#ngb-typeahead-0', { hasText: new RegExp('\\(' + credentials.username + '\\)') }).click();
        await this.page.locator('#bread-crumb-2').click();
    }

    /**
     * helper methods to get information about the course
     * */

    getRegisteredStudents(text?: string) {
        return this.page.locator('#registered-students', { hasText: text });
    }

    getCourseHeaderTitle(text?: string) {
        return this.page.locator('#course-header-title', { hasText: text });
    }

    getCourseHeaderDescription(text?: string) {
        return this.page.locator('#course-header-description', { hasText: text });
    }

    getCourseTitle(text?: string) {
        return this.page.locator('#course-title', { hasText: text });
    }

    getCourseShortName(text?: string) {
        return this.page.locator('#course-short-name', { hasText: text });
    }

    getCourseStudentGroupName(text?: string) {
        return this.page.locator('#course-student-group-name', { hasText: text });
    }

    getCourseTutorGroupName(text?: string) {
        return this.page.locator('#course-tutor-group-name', { hasText: text });
    }

    getCourseEditorGroupName(text?: string) {
        return this.page.locator('#course-editor-group-name', { hasText: text });
    }

    getCourseInstructorGroupName(text?: string) {
        return this.page.locator('#course-instructor-group-name', { hasText: text });
    }

    getCourseStartDate(text?: string) {
        return this.page.locator('#course-start-date', { hasText: text });
    }

    getCourseEndDate(text?: string) {
        return this.page.locator('#course-end-date', { hasText: text });
    }

    getCourseSemester(text?: string) {
        return this.page.locator('#course-semester', { hasText: text });
    }

    getCourseProgrammingLanguage(text?: string) {
        return this.page.locator('#course-programming-language', { hasText: text });
    }

    getCourseTestCourse(text?: string) {
        return this.page.locator('#course-test-course', { hasText: text });
    }

    getCourseOnlineCourse(text?: string) {
        return this.page.locator('#course-online-course', { hasText: text });
    }

    getCourseMaxComplaints(text?: string) {
        return this.page.locator('#course-max-complaints', { hasText: text });
    }

    getCourseMaxTeamComplaints(text?: string) {
        return this.page.locator('#course-max-team-complaints', { hasText: text });
    }

    getMaxComplaintTimeDays(text?: string) {
        return this.page.locator('#course-max-time-days', { hasText: text });
    }

    getMaxRequestMoreFeedbackTimeDays(text?: string) {
        return this.page.locator('#course-max-request-more-feedback-days', { hasText: text });
    }
}
