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
        test('Links a text unit to a competency via api and verifies it in competency detail', async ({ page, courseManagementAPIRequests, competencyManagement }) => {
            const title = makeCompetencyTitle('Single');
            const competency = await courseManagementAPIRequests.createCompetency(course, title, 'Test competency');

            // Create text unit with competency link directly
            await courseManagementAPIRequests.createTextUnit(lecture, 'Text Unit 1', 'Content for text unit', [
                { competency: { id: competency.id, type: 'competency' }, weight: 1 },
            ]);

            await competencyManagement.goto(course.id!);

            await page.getByRole('link', { name: title }).click();
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
        test('Changes the competency linked to a lecture unit via UI', async ({ page, courseManagementAPIRequests, competencyManagement }) => {
            // Three `competencyManagement.goto` calls plus several navigations to the
            // unit-management edit page accumulate well beyond the @fast 60s budget under
            // heavy multi-node load — even `test.slow()`'s 180s budget can be tight when
            // any single navigation stretches past 30s. Set an explicit 6-minute budget.
            test.setTimeout(360_000);
            const compA = await courseManagementAPIRequests.createCompetency(course, 'Comp A ' + uid, 'First competency');
            await courseManagementAPIRequests.createCompetency(course, 'Comp B ' + uid, 'Second competency');

            const textUnit = await courseManagementAPIRequests.createTextUnit(lecture, 'Text Unit', 'Content for text unit', [
                { competency: { id: compA.id, type: 'competency' }, weight: 1 },
            ]);

            await competencyManagement.goto(course.id!);
            await page.getByRole('link', { name: 'Comp A ' + uid }).click();
            await page.waitForLoadState('domcontentloaded');
            await expect(page.getByRole('heading', { name: 'Text Unit' })).toBeVisible();

            await page.goto(`/course-management/${course.id}/lectures/${lecture.id}/unit-management/text-units/${textUnit.id}/edit`);
            await page.waitForLoadState('domcontentloaded');

            await page.getByRole('checkbox', { name: 'Comp A ' + uid }).uncheck();
            await page.getByRole('checkbox', { name: 'Comp B ' + uid }).check();

            await page.click('#submitButton');
            await page.waitForLoadState('domcontentloaded');

            await competencyManagement.goto(course.id!);
            await page.getByRole('link', { name: 'Comp A ' + uid }).click();
            await page.waitForLoadState('domcontentloaded');
            await expect(page.getByRole('heading', { name: 'Text Unit' })).not.toBeVisible();

            await competencyManagement.goto(course.id!);
            await page.getByRole('link', { name: 'Comp B ' + uid }).click();
            await page.waitForLoadState('domcontentloaded');
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
