import { test } from '../../support/fixtures';
import { admin } from '../../support/users';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { expect } from '@playwright/test';
import { UnitType } from '../../support/pageobjects/lecture/LectureManagementPage';
import { generateUUID, setMonacoEditorContentByLocator } from '../../support/utils';
import { SEED_COURSES } from '../../support/seedData';

const course = { id: SEED_COURSES.atlas1.id } as any;
const uid = generateUUID();

test.describe('Competency Lecture Unit Linking', { tag: '@fast' }, () => {
    let lecture: Lecture;
    // Each test creates its own competency — use a function to get unique names
    const makeCompetencyTitle = (suffix: string) => `Comp${suffix} ${uid}`;

    test.beforeEach('Setup lecture', async ({ login, courseManagementAPIRequests }) => {
        await login(admin);
        lecture = await courseManagementAPIRequests.createLecture(course, 'Test Lecture ' + uid);
        // Enable learning paths if not already enabled (idempotent)
        try {
            await courseManagementAPIRequests.enableLearningPaths(course);
        } catch {
            // Already enabled from a previous run
        }
    });

    // Seed courses are persistent — no cleanup needed

    test.describe('Link a lecture unit to a single competency', () => {
        test('Links a text unit to a competency via api and verifies it in competency detail', async ({ page, courseManagementAPIRequests }) => {
            const title = makeCompetencyTitle('Single');
            const competency = await courseManagementAPIRequests.createCompetency(course, title, 'Test competency');

            // Create text unit with competency link directly
            await courseManagementAPIRequests.createTextUnit(lecture, 'Text Unit 1', 'Content for text unit', [
                { competency: { id: competency.id, type: 'competency' }, weight: 1 },
            ]);

            // Navigate straight to the student-facing competency detail instead of clicking the
            // competency link in the management list: that link crosses into a different lazily
            // loaded route (/courses/:id/competencies/:id), and under multi-node load the click
            // occasionally does not trigger the navigation (the page stays on the management list).
            // A direct goto goes through the fixture's bootstrap-recovery wrapper and is deterministic.
            await page.goto(`/courses/${course.id}/competencies/${competency.id}`);
            await page.waitForLoadState('domcontentloaded');

            await expect(page.getByRole('heading', { name: 'Text Unit 1' })).toBeVisible();
        });
    });

    test.describe('Link multiple lecture units to the same competency', () => {
        test('Links multiple text units to a competency via api and verifies all appear in competency detail', async ({
            page,
            courseManagementAPIRequests,
            competencyManagement,
        }) => {
            const title = makeCompetencyTitle('Multi');
            const competency = await courseManagementAPIRequests.createCompetency(course, title, 'Test competency');

            await courseManagementAPIRequests.createTextUnit(lecture, 'Text Unit 1', 'Content for text unit 1', [
                { competency: { id: competency.id, type: 'competency' }, weight: 1 },
            ]);
            await courseManagementAPIRequests.createTextUnit(lecture, 'Text Unit 2', 'Content for text unit 2', [
                { competency: { id: competency.id, type: 'competency' }, weight: 1 },
            ]);
            await courseManagementAPIRequests.createTextUnit(lecture, 'Text Unit 3', 'Content for text unit 3', [
                { competency: { id: competency.id, type: 'competency' }, weight: 1 },
            ]);

            await competencyManagement.goto(course.id!);

            await page.getByRole('link', { name: title }).click();
            await page.waitForLoadState('domcontentloaded');

            await expect(page.getByRole('heading', { name: 'Text Unit 1' })).toBeVisible();
            await expect(page.getByRole('heading', { name: 'Text Unit 2' })).toBeVisible();
            await expect(page.getByRole('heading', { name: 'Text Unit 3' })).toBeVisible();
        });
    });

    test.describe('Link a lecture unit to competency through UI', () => {
        test('Links a lecture unit to competency through lecture unit creation page', async ({ page, courseManagementAPIRequests, lectureManagement, competencyManagement }) => {
            await courseManagementAPIRequests.createCompetency(course, 'UI Link ' + uid, 'Competency for UI linking test');

            await page.goto(`/course-management/${course.id}/lectures/${lecture.id}/unit-management`);
            await page.waitForLoadState('domcontentloaded');

            await lectureManagement.openCreateUnit(UnitType.TEXT);
            await page.fill('#name', 'UI Created Text Unit');
            // Use the specific container for the content Monaco editor (id="content" in text-unit-form.component.html)
            const contentEditor = page.locator('#content');
            await setMonacoEditorContentByLocator(page, contentEditor, 'Content created through UI');

            await page.getByRole('checkbox', { name: 'UI Link ' + uid }).check();

            await page.click('#submitButton');
            await page.waitForLoadState('domcontentloaded');

            await expect(page.getByRole('heading', { name: 'UI Created Text Unit' })).toBeVisible();

            await competencyManagement.goto(course.id!);

            await page.getByRole('link', { name: 'UI Link ' + uid }).click();
            await page.waitForLoadState('domcontentloaded');
            await expect(page.getByRole('heading', { name: 'UI Created Text Unit' })).toBeVisible();
        });
    });

    test.describe('Update/change the competency linked to a lecture unit', () => {
        test('Changes the competency linked to a lecture unit via UI', async ({ page, courseManagementAPIRequests }) => {
            // The fixture's bootstrap recovery can add several bounded reloads to each navigation under heavy multi-node load.
            test.setTimeout(360_000);
            page.setDefaultNavigationTimeout(15_000);
            const compATitle = 'Comp A ' + uid;
            const compBTitle = 'Comp B ' + uid;
            const compA = await courseManagementAPIRequests.createCompetency(course, compATitle, 'First competency');
            const compB = await courseManagementAPIRequests.createCompetency(course, compBTitle, 'Second competency');

            const openCompetencyDetail = async (competencyId: number, title: string) => {
                let detailNavigationError: unknown;
                for (let attempt = 0; attempt < 2; attempt++) {
                    try {
                        await page.goto(`/courses/${course.id}/competencies/${competencyId}`, { waitUntil: 'domcontentloaded', timeout: 15_000 });
                        await expect(page.getByRole('heading', { name: title })).toBeVisible({ timeout: 15_000 });
                        return;
                    } catch (error) {
                        detailNavigationError = error;
                    }
                }
                throw new Error(`Competency detail ${competencyId} did not render after 2 attempts`, { cause: detailNavigationError });
            };

            const textUnit = await courseManagementAPIRequests.createTextUnit(lecture, 'Text Unit', 'Content for text unit', [
                { competency: { id: compA.id, type: 'competency' }, weight: 1 },
            ]);

            await openCompetencyDetail(compA.id!, compATitle);
            await expect(page.getByRole('heading', { name: 'Text Unit' })).toBeVisible();

            // Anchor on the edit form's submit button before interacting with the competency
            // checkboxes. Under heavy multi-node CI load the text-unit edit page's lazy
            // chunk occasionally fails to load — leaving the form unrendered or redirecting
            // to /courses. Retry the navigation once if the submit button does not appear.
            const editUrl = `/course-management/${course.id}/lectures/${lecture.id}/unit-management/text-units/${textUnit.id}/edit`;
            const submitButton = page.locator('#submitButton');
            let editNavigationError: unknown;
            for (let attempt = 0; attempt < 2; attempt++) {
                try {
                    await page.goto(editUrl, { waitUntil: 'domcontentloaded', timeout: 15_000 });
                    await submitButton.waitFor({ state: 'visible', timeout: 30_000 });
                    editNavigationError = undefined;
                    break;
                } catch (error) {
                    editNavigationError = error;
                }
            }
            if (editNavigationError) {
                throw new Error(`Text-unit edit form did not render at ${editUrl} after 2 attempts`, { cause: editNavigationError });
            }
            await page.getByRole('checkbox', { name: 'Comp A ' + uid }).waitFor({ state: 'visible', timeout: 30_000 });

            await page.getByRole('checkbox', { name: 'Comp A ' + uid }).uncheck();
            await page.getByRole('checkbox', { name: 'Comp B ' + uid }).check();

            const updateResponse = page.waitForResponse(
                (response) => response.url().includes(`/api/lecture/lectures/${lecture.id}/text-units`) && response.request().method() === 'PUT' && response.status() === 200,
            );
            await page.click('#submitButton');
            await updateResponse;
            await page.waitForURL('**/unit-management', { timeout: 30_000 });

            await openCompetencyDetail(compA.id!, compATitle);
            await expect(page.getByRole('heading', { name: 'Text Unit' })).not.toBeVisible();

            await openCompetencyDetail(compB.id!, compBTitle);
            await expect(page.getByRole('heading', { name: 'Text Unit' })).toBeVisible();
        });
    });

    test.describe('Remove competency from a lecture unit', () => {
        test('Unlinks a lecture unit from a competency via UI', async ({ page, courseManagementAPIRequests, competencyManagement }) => {
            // Two competencyManagement.goto calls + unit-management navigation + form save
            // routinely exceed the @fast 60s budget under heavy multi-node load. Triple it.
            test.slow();
            const title = makeCompetencyTitle('Unlink');
            const competency = await courseManagementAPIRequests.createCompetency(course, title, 'Test competency');

            const textUnit = await courseManagementAPIRequests.createTextUnit(lecture, 'Text Unit', 'Content for text unit', [
                { competency: { id: competency.id, type: 'competency' }, weight: 1 },
            ]);

            await competencyManagement.goto(course.id!);
            await page.getByRole('link', { name: title }).click();
            await page.waitForLoadState('domcontentloaded');
            await expect(page.getByRole('heading', { name: 'Text Unit' })).toBeVisible();

            await page.goto(`/course-management/${course.id}/lectures/${lecture.id}/unit-management/text-units/${textUnit.id}/edit`);
            await page.waitForLoadState('domcontentloaded');

            await page.getByRole('checkbox', { name: title }).uncheck();

            await page.click('#submitButton');
            await page.waitForLoadState('domcontentloaded');

            await competencyManagement.goto(course.id!);
            await page.getByRole('link', { name: title }).click();
            await page.waitForLoadState('domcontentloaded');
            await expect(page.getByRole('heading', { name: 'Text Unit' })).not.toBeVisible();

            await page.reload();
            await page.waitForLoadState('domcontentloaded');
            await expect(page.getByRole('heading', { name: 'Text Unit' })).not.toBeVisible();
        });
    });
});
