import { Page, expect } from '@playwright/test';
import { UserCredentials } from '../../users';
import { COURSE_ADMIN_BASE, COURSE_BASE } from '../../constants';
import { Course } from 'app/entities/course.model';

/**
 * A class which encapsulates UI selectors and actions for the Course Management page.
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
     * Opens the exercises of a course.
     * @param courseID the id of the course
     */
    async openExercisesOfCourse(courseID: number) {
        await this.getCourse(courseID).locator('#course-card-open-exercises').click();
        await this.page.waitForURL('**/exercises**');
    }

    /**
     * Opens the students overview page of a course.
     * @param courseID the id of the course
     */
    async openStudentOverviewOfCourse(courseID: number) {
        await this.page.locator('#open-student-management-' + courseID).click();
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
        const responsePromise = this.page.waitForResponse(`${COURSE_ADMIN_BASE}/${course.id}`);
        await this.page.locator('#delete').click();
        await responsePromise;
    }

    /**
     * Adds the user to the student group of the course
     * @param credentials the user that gets added to the student group of the course
     * */
    async addStudentToCourse(credentials: UserCredentials) {
        const responsePromise = this.page.waitForResponse(`${COURSE_BASE}/*/students/${credentials.username}`);
        await this.page.locator('#detail-value-artemisApp\\.course\\.studentGroupName').locator('a').click();
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
        await this.page.locator('.dropdown-item', { hasText: `(${credentials.username})` }).click();
    }

    /**
     * Opens the exams of a course.
     */
    async openExamsOfCourse(courseID: number) {
        await this.getCourse(courseID).locator('#course-card-open-exams').click();
        await this.page.waitForURL('**/exams**');
    }

    async openAssessmentDashboardOfCourse(courseID: number) {
        await this.getCourse(courseID).locator('#course-card-open-assessment-dashboard').click();
        await this.page.waitForURL('**/assessment-dashboard**');
    }

    /*
     * Helper methods to get information about the course
     */

    /**
     * Retrieves the locator for the registered students section with optional text filtering.
     * @returns The locator for the registered students section.
     */
    getRegisteredStudents() {
        return this.page.locator('#registered-students');
    }

    /**
     * Retrieves the locator for the course header title.
     * @returns The locator for the course header title.
     */
    getCourseHeaderTitle() {
        return this.page.locator('#course-header-title');
    }

    /**
     * Retrieves the locator for the course header description.
     * @returns The locator for the course header description.
     */
    getCourseHeaderDescription() {
        return this.page.locator('#course-header-description');
    }

    /**
     * Retrieves the locator for the course title.
     * @returns The locator for the course title.
     */
    getCourseTitle() {
        return this.page.locator('#detail-value-artemisApp\\.course\\.title');
    }

    /**
     * Retrieves the locator for the course short name with optional text filtering.
     * @returns The locator for the course short name.
     */
    getCourseShortName() {
        return this.page.locator('#detail-value-artemisApp\\.course\\.shortName');
    }

    /**
     * Retrieves the locator for the course student group name.
     * @returns The locator for the course student group name.
     */
    getCourseStudentGroupName() {
        return this.page.locator('#detail-value-artemisApp\\.course\\.studentGroupName');
    }

    /**
     * Retrieves the locator for the course tutor group name.
     * @returns The locator for the course tutor group name.
     */
    getCourseTutorGroupName() {
        return this.page.locator('#detail-value-artemisApp\\.course\\.teachingAssistantGroupName');
    }

    /**
     * Retrieves the locator for the course editor group name.
     * @returns The locator for the course editor group name.
     */
    getCourseEditorGroupName() {
        return this.page.locator('#detail-value-artemisApp\\.course\\.editorGroupName');
    }

    /**
     * Retrieves the locator for the course instructor group.
     * @returns The locator for the course instructor group name.
     */
    getCourseInstructorGroupName() {
        return this.page.locator('#detail-value-artemisApp\\.course\\.instructorGroupName');
    }

    /**
     * Retrieves the locator for the course start date.
     * @returns The locator for the course start date.
     */
    getCourseStartDate() {
        return this.page.locator('#detail-value-artemisApp\\.course\\.startDate');
    }

    /**
     * Retrieves the locator for the course end date.
     * @returns The locator for the course end date.
     */
    getCourseEndDate() {
        return this.page.locator('#detail-value-artemisApp\\.course\\.endDate');
    }

    /**
     * Retrieves the locator for the course semester.
     * @returns The locator for the course semester.
     */
    getCourseSemester() {
        return this.page.locator('#detail-value-artemisApp\\.course\\.semester');
    }

    /**
     * Retrieves the locator for the course programming language.
     * @returns The locator for the course programming language.
     */
    getCourseProgrammingLanguage() {
        return this.page.locator('#detail-value-artemisApp\\.course\\.defaultProgrammingLanguage');
    }

    /**
     * Retrieves the locator for the test course indicator.
     * @returns The locator for the test course indicator.
     */
    getCourseTestCourse() {
        return this.page.locator('#detail-value-artemisApp\\.course\\.testCourse\\.title');
    }

    /**
     * Retrieves the locator for the online course indicator.
     * @returns The locator for the online course indicator.
     */
    getCourseOnlineCourse() {
        return this.page.locator('#detail-value-artemisApp\\.course\\.onlineCourse\\.title');
    }

    /**
     * Retrieves the locator for the maximum complaints allowed.
     * @returns The locator for the maximum complaints allowed.
     */
    getCourseMaxComplaints() {
        return this.page.locator('#detail-value-artemisApp\\.course\\.maxComplaints\\.title');
    }

    /**
     * Retrieves the locator for the maximum team complaints allowed.
     * @returns The locator for the maximum team complaints allowed.
     */
    getCourseMaxTeamComplaints() {
        return this.page.locator('#detail-value-artemisApp\\.course\\.maxTeamComplaints\\.title');
    }

    /**
     * Retrieves the locator for the maximum complaint time in days.
     * @returns The locator for the maximum complaint time in days.
     */
    getMaxComplaintTimeDays() {
        return this.page.locator('#detail-value-artemisApp\\.course\\.maxComplaintTimeDays\\.title');
    }

    /**
     * Retrieves the locator for the maximum request more feedback time in days.
     * @returns The locator for the maximum request more feedback time in days.
     */
    getMaxRequestMoreFeedbackTimeDays() {
        return this.page.locator('#detail-value-artemisApp\\.course\\.maxRequestMoreFeedbackTimeDays\\.title');
    }
}
