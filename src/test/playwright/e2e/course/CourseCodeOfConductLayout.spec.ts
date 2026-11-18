import { expect, type Page } from '@playwright/test';
import { Course } from 'app/course/shared/entities/course.model';
import { test } from '../../support/fixtures';
import { admin, studentOne } from '../../support/users';
import { expectFillsParent } from '../../support/layout';

/**
 * Writes a code of conduct that is longer than the page into the course. The course creation does not keep one, and the page that
 * asks for the agreement only exists for a course that has one. The mapping holds the fields the update endpoint accepts.
 */
async function writeLongCodeOfConduct(page: Page, courseId: number) {
    const course = await (await page.request.get(`api/course/courses/${courseId}`)).json();
    const codeOfConduct = Array.from({ length: 25 }, (_, index) => `## Section ${index + 1}\n\nA rule of the code of conduct that is long enough to need some space.\n`).join('\n');
    const update = await page.request.put(`api/course/courses/${courseId}`, {
        multipart: {
            course: {
                name: 'course',
                mimeType: 'application/json',
                buffer: Buffer.from(
                    JSON.stringify({
                        id: course.id,
                        title: course.title,
                        shortName: course.shortName,
                        semester: course.semester,
                        startDate: course.startDate,
                        endDate: course.endDate,
                        testCourse: course.testCourse,
                        maxComplaints: course.maxComplaints,
                        maxTeamComplaints: course.maxTeamComplaints,
                        maxComplaintTimeDays: course.maxComplaintTimeDays,
                        maxRequestMoreFeedbackTimeDays: course.maxRequestMoreFeedbackTimeDays,
                        maxComplaintTextLimit: course.maxComplaintTextLimit,
                        maxComplaintResponseTextLimit: course.maxComplaintResponseTextLimit,
                        maxPoints: course.maxPoints,
                        accuracyOfScores: course.accuracyOfScores,
                        timeZone: course.timeZone,
                        courseInformationSharingConfiguration: course.courseInformationSharingConfiguration,
                        courseInformationSharingMessagingCodeOfConduct: codeOfConduct,
                        onboardingDone: course.onboardingDone,
                        presentationAssessmentsEnabled: course.presentationAssessmentsEnabled ?? false,
                        gradeRelevant: course.courseConfiguration?.gradeRelevant ?? true,
                        dataRetentionHold: course.courseConfiguration?.dataRetentionHold ?? false,
                        autoOrchestratorEnabled: course.courseConfiguration?.autoOrchestratorEnabled ?? false,
                    }),
                ),
            },
        },
    });
    expect(update.ok(), await update.text()).toBeTruthy();
}

test.describe('Code of Conduct layout', { tag: '@fast' }, () => {
    let course: Course | undefined;

    test.beforeEach(async ({ login, page, courseManagementAPIRequests }) => {
        await login(admin);
        course = await courseManagementAPIRequests.createCourse({ allowMessaging: true });
        await courseManagementAPIRequests.addStudentToCourse(course, studentOne);
        await writeLongCodeOfConduct(page, course.id!);
    });

    test.afterEach(async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
        course = undefined;
    });

    test('fills the communication page down to its bottom edge and scrolls inside of its panel', async ({ login, page }) => {
        await login(studentOne, `/courses/${course!.id}/communication`);
        const panel = page.getByTestId('code-of-conduct-panel');
        await expect(panel).toBeVisible();

        for (const viewport of [
            { width: 1920, height: 1080 },
            { width: 1366, height: 768 },
        ]) {
            await page.setViewportSize(viewport);
            // The panel ends where the page that holds it ends, so that no unused area is left below the text
            await expectFillsParent(panel, panel.locator('xpath=..'), ['bottom'], { name: `Code of Conduct at ${viewport.width}px` });
        }
        // The text is longer than the panel and scrolls inside of it, not beyond it
        expect(await panel.evaluate((element) => element.scrollHeight > element.clientHeight)).toBe(true);
    });
});
