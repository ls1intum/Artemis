import { Page, expect } from '@playwright/test';
import { UserCredentials } from '../../users';
import { BASE_API, COURSE_BASE } from '../../constants';
import { Course } from 'app/entities/course.model';

/**
 * A class which encapsulates UI selectors and actions for the course management page.
 */
export class CourseManagementPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    /**
     * Opens the course creation page.
     */
    async openCourseCreation() {
        await this.page.locator('#create-course').click();
    }

    /**
     * Opens the course edit page.
     */
    async openCourseEdit() {
        await this.page.locator('#edit-course').click();
    }

    /**
     * @returns Returns the locator containing the root element of the course card of our created course.
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
     * Deletes the specified course.
     * @param course - The course to be deleted.
     */
    async deleteCourse(course: Course) {
        await this.page.locator('#delete-course').click();
        await expect(this.page.locator('#delete')).toBeDisabled();
        await this.page.locator('#confirm-entity-name').fill(course.title!);
        const responsePromise = this.page.waitForResponse(BASE_API + 'admin/courses/' + course.id);
        await this.page.locator('#delete').click();
        await responsePromise;
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

    /**
     * Removes the first user from the registered students.
     */
    async removeFirstUser() {
        await this.page.locator('#registered-students button[jhideletebutton]').first().click();
        await this.page.locator('.modal #delete').click();
    }

    /**
     * Confirms the user into the group.
     * @param credentials - The user credentials to be confirmed into the group.
     */
    private async confirmUserIntoGroup(credentials: UserCredentials) {
        await this.page.locator('#typeahead-basic').fill(credentials.username);
        await this.page.keyboard.press('Enter');
        const userResults = this.page.locator('.ngb-highlight', { hasText: credentials.username });
        await userResults.first().click();
        await this.page.locator('#bread-crumb-2').click();
    }

    /*
     * Helper methods to get information about the course
     */

    /**
     * Retrieves the locator for the registered students section with optional text filtering.
     * @param text - Optional text to filter the registered students section.
     * @returns The locator for the registered students section.
     */
    getRegisteredStudents(text?: string) {
        return this.page.locator('#registered-students', { hasText: text });
    }

    /**
     * Retrieves the locator for the course header title with optional text filtering.
     * @param text - Optional text to filter the course header title.
     * @returns The locator for the course header title.
     */
    getCourseHeaderTitle(text?: string) {
        return this.page.locator('#course-header-title', { hasText: text });
    }

    /**
     * Retrieves the locator for the course header description with optional text filtering.
     * @param text - Optional text to filter the course header description.
     * @returns The locator for the course header description.
     */
    getCourseHeaderDescription(text?: string) {
        return this.page.locator('#course-header-description', { hasText: text });
    }

    /**
     * Retrieves the locator for the course title with optional text filtering.
     * @param text - Optional text to filter the course title.
     * @returns The locator for the course title.
     */
    getCourseTitle(text?: string) {
        return this.page.locator('#course-title', { hasText: text });
    }

    /**
     * Retrieves the locator for the course short name with optional text filtering.
     * @param text - Optional text to filter the course short name.
     * @returns The locator for the course short name.
     */
    getCourseShortName(text?: string) {
        return this.page.locator('#course-short-name', { hasText: text });
    }

    /**
     * Retrieves the locator for the course student group name with optional text filtering.
     * @param text - Optional text to filter the course student group name.
     * @returns The locator for the course student group name.
     */
    getCourseStudentGroupName(text?: string) {
        return this.page.locator('#course-student-group-name', { hasText: text });
    }

    /**
     * Retrieves the locator for the course tutor group name with optional text filtering.
     * @param text - Optional text to filter the course tutor group name.
     * @returns The locator for the course tutor group name.
     */
    getCourseTutorGroupName(text?: string) {
        return this.page.locator('#course-tutor-group-name', { hasText: text });
    }

    /**
     * Retrieves the locator for the course editor group name with optional text filtering.
     * @param text - Optional text to filter the course editor group name.
     * @returns The locator for the course editor group name.
     */
    getCourseEditorGroupName(text?: string) {
        return this.page.locator('#course-editor-group-name', { hasText: text });
    }

    /**
     * Retrieves the locator for the course instructor group name with optional text filtering.
     * @param text - Optional text to filter the course instructor group name.
     * @returns The locator for the course instructor group name.
     */
    getCourseInstructorGroupName(text?: string) {
        return this.page.locator('#course-instructor-group-name', { hasText: text });
    }

    /**
     * Retrieves the locator for the course start date with optional text filtering.
     * @param text - Optional text to filter the course start date.
     * @returns The locator for the course start date.
     */
    getCourseStartDate(text?: string) {
        return this.page.locator('#course-start-date', { hasText: text });
    }

    /**
     * Retrieves the locator for the course end date with optional text filtering.
     * @param text - Optional text to filter the course end date.
     * @returns The locator for the course end date.
     */
    getCourseEndDate(text?: string) {
        return this.page.locator('#course-end-date', { hasText: text });
    }

    /**
     * Retrieves the locator for the course semester with optional text filtering.
     * @param text - Optional text to filter the course semester.
     * @returns The locator for the course semester.
     */
    getCourseSemester(text?: string) {
        return this.page.locator('#course-semester', { hasText: text });
    }

    /**
     * Retrieves the locator for the course programming language with optional text filtering.
     * @param text - Optional text to filter the course programming language.
     * @returns The locator for the course programming language.
     */
    getCourseProgrammingLanguage(text?: string) {
        return this.page.locator('#course-programming-language', { hasText: text });
    }

    /**
     * Retrieves the locator for the test course indicator with optional text filtering.
     * @param text - Optional text to filter the test course indicator.
     * @returns The locator for the test course indicator.
     */
    getCourseTestCourse(text?: string) {
        return this.page.locator('#course-test-course', { hasText: text });
    }

    /**
     * Retrieves the locator for the online course indicator with optional text filtering.
     * @param text - Optional text to filter the online course indicator.
     * @returns The locator for the online course indicator.
     */
    getCourseOnlineCourse(text?: string) {
        return this.page.locator('#course-online-course', { hasText: text });
    }

    /**
     * Retrieves the locator for the maximum complaints allowed with optional text filtering.
     * @param text - Optional text to filter the maximum complaints allowed.
     * @returns The locator for the maximum complaints allowed.
     */
    getCourseMaxComplaints(text?: string) {
        return this.page.locator('#course-max-complaints', { hasText: text });
    }

    /**
     * Retrieves the locator for the maximum team complaints allowed with optional text filtering.
     * @param text - Optional text to filter the maximum team complaints allowed.
     * @returns The locator for the maximum team complaints allowed.
     */
    getCourseMaxTeamComplaints(text?: string) {
        return this.page.locator('#course-max-team-complaints', { hasText: text });
    }

    /**
     * Retrieves the locator for the maximum complaint time in days with optional text filtering.
     * @param text - Optional text to filter the maximum complaint time in days.
     * @returns The locator for the maximum complaint time in days.
     */
    getMaxComplaintTimeDays(text?: string) {
        return this.page.locator('#course-max-time-days', { hasText: text });
    }

    /**
     * Retrieves the locator for the maximum request more feedback time in days with optional text filtering.
     * @param text - Optional text to filter the maximum request more feedback time in days.
     * @returns The locator for the maximum request more feedback time in days.
     */
    getMaxRequestMoreFeedbackTimeDays(text?: string) {
        return this.page.locator('#course-max-request-more-feedback-days', { hasText: text });
    }
}
