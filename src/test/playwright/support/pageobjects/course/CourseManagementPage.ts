import { Page, expect } from '@playwright/test';
import { UserCredentials } from '../../users';
import { COURSE_ADMIN_BASE } from '../../constants';
import { Course } from 'app/core/course/shared/entities/course.model';
import { CourseSummary } from '../../../e2e/course/CourseManagement.spec';

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
     * Opens the course settings page.
     */
    async openCourseSettings() {
        await this.page.locator('#course-settings').click();
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

    private async assertCourseSummary(expectedCourseSummary: CourseSummary) {
        // Verify test course indicator is shown in the delete question text
        if (expectedCourseSummary.isTestCourse) {
            await expect(this.page.locator('strong', { hasText: 'test course' })).toBeVisible();
        }

        // The delete dialog now shows a table with label/value pairs in cells
        // Each row has: label cell, value cell, (optional second pair: label cell, value cell)
        const summaryItems = [
            { label: 'Students', expected: expectedCourseSummary.students },
            { label: 'Tutors', expected: expectedCourseSummary.tutors },
            { label: 'Editors', expected: expectedCourseSummary.editors },
            { label: 'Instructors', expected: expectedCourseSummary.instructors },
            { label: 'Exams', expected: expectedCourseSummary.exams },
            { label: 'Lectures', expected: expectedCourseSummary.lectures },
            { label: 'Programming Exercises', expected: expectedCourseSummary.programingExercises },
            { label: 'Modeling Exercises', expected: expectedCourseSummary.modelingExercises },
            { label: 'Quiz Exercises', expected: expectedCourseSummary.quizExercises },
            { label: 'Text Exercises', expected: expectedCourseSummary.textExercises },
            { label: 'File Upload Exercises', expected: expectedCourseSummary.fileUploadExercises },
            { label: 'Posts', expected: expectedCourseSummary.communicationPosts },
        ];

        for (const { label, expected } of summaryItems) {
            // Find the label cell and get the value from the next sibling cell
            const labelCell = this.page.locator('.item-label-cell', { hasText: label }).first();
            const valueCell = labelCell.locator('+ .item-value-cell');
            const actualValue = await valueCell.innerText();
            expect(Number(actualValue)).toBe(expected);
        }
    }

    /**
     * Deletes the specified course.
     * @param course - The course to be deleted.
     * @param expectedCourseSummary - if defined, the course summary is asserted to contain the expected values before deletion
     */
    async deleteCourse(course: Course, expectedCourseSummary?: CourseSummary) {
        await this.page.locator('#delete-course').click();
        const deleteButton = this.page.getByTestId('delete-dialog-confirm-button');
        await expect(deleteButton).toBeDisabled();

        if (expectedCourseSummary) {
            await this.assertCourseSummary(expectedCourseSummary);
        }

        await this.page.locator('#confirm-entity-name').fill(course.title!);
        const responsePromise = this.page.waitForResponse(`${COURSE_ADMIN_BASE}/${course.id}`);
        await deleteButton.click();
        await responsePromise;
    }

    /**
     * Adds the user to a specific group of the course.
     * @param credentials The user that gets added to the group.
     * @param groupType The type of group (e.g., 'students', 'tutors', 'instructors').
     * @param selector The selector for the group action button.
     */
    private async addUserToGroup(credentials: UserCredentials, groupType: string, selector: string) {
        const responsePromise = this.page.waitForResponse(`api/core/courses/*/${groupType}/${credentials.username}`);
        await this.page.locator('#user-management-dropdown').click();
        await this.page.locator(selector).click();
        await this.confirmUserIntoGroup(credentials);
        await responsePromise;
    }

    /**
     * Adds the user to the student group of the course.
     * @param credentials The user that gets added to the student group of the course.
     */
    async addStudentToCourse(credentials: UserCredentials) {
        await this.addUserToGroup(credentials, 'students', '#add-student');
    }

    /**
     * Adds the user to the tutor group of the course.
     * @param credentials The user that gets added to the tutor group of the course.
     */
    async addTutorToCourse(credentials: UserCredentials) {
        await this.addUserToGroup(credentials, 'tutors', '#add-tutor');
    }

    /**
     * Adds the user to the instructor group of the course.
     * @param credentials The user that gets added to the instructor group of the course.
     */
    async addInstructorToCourse(credentials: UserCredentials) {
        await this.addUserToGroup(credentials, 'instructors', '#add-instructor');
    }

    /**
     * Removes the first user from the registered students.
     */
    async removeFirstUser() {
        await this.page.locator('#registered-students button[jhideletebutton]').first().click();
        await this.page.getByTestId('delete-dialog-confirm-button').click();
    }

    async updateCourse(course: Course) {
        const response = this.page.waitForResponse(`api/core/courses/${course.id}`);
        await this.page.locator('#save-entity').click();
        await response;
    }

    async checkCourseHasNoIcon() {
        await expect(this.page.locator('#delete-course-icon')).not.toBeVisible();
        await expect(this.page.locator('.no-image')).toBeVisible();
    }

    async removeIconFromCourse() {
        await this.page.locator('#delete-course-icon').click();
        await this.checkCourseHasNoIcon();
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

    async openSubmissionsForExerciseAndCourse(courseID: number, exerciseID: number) {
        await this.getCourse(courseID).locator('#course-card-open-exercises').click();
        await this.page.waitForURL('**/exercises**');
        await this.page.click(`[href="/course-management/${courseID}/modeling-exercises/${exerciseID}/scores"]`);
        await this.page.waitForURL('**/scores');
    }

    async checkIfStudentSubmissionExists(studentName: string) {
        await expect(this.page.locator('.datatable-body-row', { hasText: studentName })).toBeVisible();
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
    getCourseSidebarTitle() {
        return this.page.locator('#test-course-title');
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
    getNumberOfStudents() {
        return this.page.locator('#number-of-students');
    }

    /**
     * Retrieves the locator for the course tutor group name.
     * @returns The locator for the course tutor group name.
     */
    getCourseTutorGroupName() {
        return this.page.locator('#detail-value-artemisApp\\.course\\.teachingAssistantGroupName');
    }

    getNumberOfTutors() {
        return this.page.locator('#number-of-tutors');
    }

    /**
     * Retrieves the locator for the course editor group name.
     * @returns The locator for the course editor group name.
     */
    getCourseEditorGroupName() {
        return this.page.locator('#detail-value-artemisApp\\.course\\.editorGroupName');
    }

    getNumberOfEditors() {
        return this.page.locator('#number-of-editors');
    }

    /**
     * Retrieves the locator for the course instructor group.
     * @returns The locator for the course instructor group name.
     */
    getCourseInstructorGroupName() {
        return this.page.locator('#detail-value-artemisApp\\.course\\.instructorGroupName');
    }

    getNumberOfInstructors() {
        return this.page.locator('#number-of-instructors');
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
