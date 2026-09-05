import { Page, expect } from '@playwright/test';
import { UserCredentials } from '../../users';
import { COURSE_ADMIN_BASE } from '../../constants';
import { Course } from 'app/course/shared/entities/course.model';
import { CourseSummary } from '../../../e2e/course/CourseManagement.spec';

/**
 * A class which encapsulates UI selectors and actions for course management flows.
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
     * <p>
     * Waits explicitly for the settings tab to render before clicking. The course detail layout is
     * hydrated asynchronously after openCourse() navigates, so the bare .click() races the render
     * under parallel CI load (see CourseManagement.spec.ts:260, where the deletion-summary test
     * builds up 25+ child entities via API before reaching the UI step).
     */
    async openCourseSettings() {
        const settings = this.page.locator('#course-settings');
        await settings.waitFor({ state: 'visible', timeout: 30_000 });
        await settings.click();
    }

    /**
     * @returns the consolidated overview card for the requested course
     * This can be used to find specific elements within this course card.
     */
    getCourse(courseID: number) {
        const header = this.page.locator(`#course-${courseID}-header`);
        return this.page.locator('jhi-overview-course-card', { has: header });
    }

    private getCourseManagementLink(courseID: number) {
        return this.getCourse(courseID).locator(`a[href="/course-management/${courseID}"]`);
    }

    private async openManagementSectionOfCourse(courseID: number, section: string) {
        await this.openCourse(courseID);

        const expectedUrl = new RegExp(`/course-management/${courseID}/${section}(?:/|$)`);
        const sectionUrl = `/course-management/${courseID}/${section}`;
        const sectionLinkSelector = `a[href="/course-management/${courseID}/${section}"]`;
        const sectionLinkAttached = await this.page
            .locator(sectionLinkSelector)
            .first()
            .waitFor({ state: 'attached', timeout: 5_000 })
            .then(() => true)
            .catch(() => false);

        if (sectionLinkAttached) {
            if (!(await this.page.locator(sectionLinkSelector).first().isVisible())) {
                // Less frequently used management sections move into the responsive "More" menu.
                const moreMenu = this.page.locator('.three-dots:visible').first();
                const moreMenuVisible = await moreMenu
                    .waitFor({ state: 'visible', timeout: 5_000 })
                    .then(() => true)
                    .catch(() => false);
                if (moreMenuVisible) {
                    await moreMenu.click();
                }
            }

            const sectionLink = this.page.locator(`${sectionLinkSelector}:visible`).first();
            const sectionLinkVisible = await sectionLink
                .waitFor({ state: 'visible', timeout: 5_000 })
                .then(() => true)
                .catch(() => false);
            if (sectionLinkVisible) {
                await sectionLink.click();
                const settled = await this.page
                    .waitForURL(expectedUrl, { timeout: 15_000 })
                    .then(() => true)
                    .catch(() => false);
                if (settled) {
                    return;
                }
            }
        }

        // The management overview redirects instructors to onboarding while course setup is incomplete. Section
        // routes remain accessible, so fall back directly instead of waiting for navigation elements that onboarding
        // intentionally does not render. This also recovers from a responsive menu or router click that did not settle.
        await this.page.goto(sectionUrl);
        await this.page.waitForURL(expectedUrl, { timeout: 30_000 });
    }

    /**
     * Opens the exercises of a course.
     * @param courseID the id of the course
     */
    async openExercisesOfCourse(courseID: number) {
        await this.openManagementSectionOfCourse(courseID, 'exercises');
    }

    /**
     * Opens the students overview page of a course.
     * @param courseID the id of the course
     */
    async openStudentOverviewOfCourse(courseID: number) {
        await this.openCourse(courseID);
        await this.page.locator('#number-of-students').click();
        await this.page.waitForURL(`**/course-management/${courseID}/members/students`);
    }

    /**
     * Opens a course.
     * @param courseID
     */
    async openCourse(courseID: number) {
        // The consolidated list keeps the student course link on the card itself and exposes a
        // separate management action to staff. Use that action so the flow enters the management shell.
        const managementLink = this.getCourseManagementLink(courseID);
        await managementLink.waitFor({ state: 'visible', timeout: 30_000 });
        await managementLink.click();
        // Wait for SPA navigation into the course detail to complete before subsequent steps
        // (e.g. openCourseSettings) race the next render. Under heavy multi-node CI load the
        // click occasionally completes without triggering Angular's router (the SPA stays at
        // /course-management instead of advancing to /course-management/<id>). Fall back to an
        // explicit goto so the test does not consume the whole budget waiting on a missing nav.
        const expectedUrl = new RegExp(`/course-management/${courseID}(/|$)`);
        const urlSettled = await this.page
            .waitForURL(expectedUrl, { timeout: 15_000 })
            .then(() => true)
            .catch(() => false);
        if (!urlSettled) {
            await this.page.goto(`/course-management/${courseID}`);
            await this.page.waitForURL(expectedUrl, { timeout: 30_000 });
        }
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
        const responsePromise = this.page.waitForResponse(`api/course/courses/*/${groupType}/${credentials.username}`);
        // Open the user-management dropdown and the add-<group> action, which navigates to the group page.
        await this.page.locator('[data-testid="user-management-dropdown"]').click();
        await this.page.locator(selector).click();
        // The group page hosts a PrimeNG autocomplete to search for and add users.
        const searchInput = this.page.locator('p-autocomplete input');
        await searchInput.waitFor({ state: 'visible', timeout: 30_000 });
        await searchInput.fill(credentials.username);
        // Pick the matching suggestion (rendered as "Name (login)"). The closing parenthesis keeps the match
        // unambiguous, e.g. it selects artemis_test_user_1 rather than artemis_test_user_10.
        await this.page
            .getByTestId('user-autocomplete-option')
            .filter({ hasText: `(${credentials.username})` })
            .click();
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
     * <p>
     * The students table re-renders after `addStudent()` and the bare click() can race that
     * second render under parallel load — the row that previously had the delete button is
     * detached and the locator points at nothing. Wait for the delete button to be attached
     * and visible before clicking.
     */
    async removeFirstUser() {
        const deleteButton = this.page.locator('jhi-table-view button[jhideletebutton]').first();
        await deleteButton.waitFor({ state: 'visible', timeout: 30_000 });
        await deleteButton.click();
        await this.page.getByTestId('delete-dialog-confirm-button').click();
    }

    async updateCourse(course: Course) {
        const response = this.page.waitForResponse(`api/course/courses/${course.id}`);
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
     * Opens the exams of a course.
     */
    async openExamsOfCourse(courseID: number) {
        await this.openManagementSectionOfCourse(courseID, 'exams');
    }

    async openAssessmentDashboardOfCourse(courseID: number) {
        await this.openManagementSectionOfCourse(courseID, 'assessment-dashboard');
    }

    async openSubmissionsForExerciseAndCourse(courseID: number, exerciseID: number) {
        await this.openExercisesOfCourse(courseID);
        await this.page.click(`[href="/course-management/${courseID}/modeling-exercises/${exerciseID}/scores"]`);
        await this.page.waitForURL('**/scores');
    }

    async checkIfStudentSubmissionExists(studentName: string) {
        await expect(this.page.getByRole('row').filter({ hasText: studentName })).toBeVisible();
    }

    /*
     * Helper methods to get information about the course
     */

    /**
     * Retrieves the locator for the registered students section with optional text filtering.
     * @returns The locator for the registered students section.
     */
    getRegisteredStudents() {
        return this.page.locator('jhi-table-view tbody tr');
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

    getNumberOfStudents() {
        return this.page.locator('#number-of-students');
    }

    getNumberOfTutors() {
        return this.page.locator('#number-of-tutors');
    }

    getNumberOfEditors() {
        return this.page.locator('#number-of-editors');
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
