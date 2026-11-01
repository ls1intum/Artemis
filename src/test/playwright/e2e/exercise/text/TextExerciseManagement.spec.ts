import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { test } from '../../../support/fixtures';
import { admin } from '../../../support/users';
import { generateUUID, readResponseJson } from '../../../support/utils';
import dayjs from 'dayjs';
import { expect, Locator, Page } from '@playwright/test';
import { ExampleSubmission } from 'app/assessment/shared/entities/example-submission.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { SEED_COURSES } from '../../../support/seedData';
import { expectInside, expectNoHorizontalOverflow, expectNoOverlap, measure } from '../../../support/layout';

const course = { id: SEED_COURSES.exerciseManagement.id } as any;

const responsiveWidths = [1516, 1200, 1079, 812, 756, 390];

async function expectFullTextInside(element: Locator, name: string): Promise<void> {
    await expect
        .poll(
            () =>
                element.evaluate((node) => {
                    const bounds = node.getBoundingClientRect();
                    const range = document.createRange();
                    range.selectNodeContents(node);
                    return {
                        horizontal: node.scrollWidth <= node.clientWidth + 1,
                        vertical: node.scrollHeight <= node.clientHeight + 1,
                        textInside: Array.from(range.getClientRects()).every(
                            (rect) => rect.left >= bounds.left - 1 && rect.right <= bounds.right + 1 && rect.top >= bounds.top - 1 && rect.bottom <= bounds.bottom + 1,
                        ),
                    };
                }),
            { message: `${name}: every text fragment must fit without clipping or ellipsis` },
        )
        .toEqual({ horizontal: true, vertical: true, textInside: true });
}

async function usePageLanguage(page: Page, language: 'en' | 'de'): Promise<void> {
    // Keep localization local to this browser; changing the shared admin's language would race other workers.
    await page.route('**/api/core/public/account', async (route) => {
        const response = await route.fetch();
        await route.fulfill({ response, json: { ...(await response.json()), langKey: language } });
    });
}

test.describe('Text exercise management', { tag: '@slow' }, () => {
    test.describe('Text exercise creation', () => {
        let createdExerciseId: number | undefined;

        test('Creates a text exercise in the UI', async ({
            login,
            page,
            navigationBar,
            courseManagement,
            courseManagementExercises,
            textExerciseCreation,
            textExerciseExampleSubmissions,
            textExerciseExampleSubmissionCreation,
        }) => {
            // Login → openCourseManagement → openExercises → createTextExercise + form +
            // example-submission flow + up-to-three 20s polling reloads on the exercises list
            // exceeds the 90s @slow budget under multi-node CI load. Lift the per-test
            // timeout to 270s via test.slow() — observed worst case ~200s.
            test.slow();
            await login(admin, '/');
            await navigationBar.openCourseManagement();
            await courseManagement.openExercisesOfCourse(course.id!);
            await courseManagementExercises.createTextExercise();

            // Fill out text exercise form
            const exerciseTitle = 'text exercise' + generateUUID();
            const releaseDate = dayjs().startOf('minute');
            const startDate = releaseDate.add(1, 'hour');
            const dueDate = releaseDate.add(1, 'day');
            const assessmentDueDate = releaseDate.add(2, 'days');
            await textExerciseCreation.setTitle(exerciseTitle);
            await textExerciseCreation.setReleaseDate(releaseDate);
            await textExerciseCreation.setStartDate(startDate);
            await textExerciseCreation.setDueDate(dueDate);
            await textExerciseCreation.setAssessmentDueDate(assessmentDueDate);
            await textExerciseCreation.typeMaxPoints(10);
            const problemStatement = 'This is a problem statement';
            const exampleSolution = 'E = mc^2';
            await textExerciseCreation.typeProblemStatement(problemStatement);
            await textExerciseCreation.typeExampleSolution(exampleSolution);
            const exerciseCreateResponse = await textExerciseCreation.create();
            const exercise: TextExercise = await readResponseJson(exerciseCreateResponse);
            createdExerciseId = exercise.id;
            expect(dayjs(exercise.releaseDate).isSame(releaseDate)).toBe(true);
            expect(dayjs(exercise.startDate).isSame(startDate)).toBe(true);
            expect(dayjs(exercise.dueDate).isSame(dueDate)).toBe(true);
            expect(dayjs(exercise.assessmentDueDate).isSame(assessmentDueDate)).toBe(true);

            // Create an example submission
            await courseManagementExercises.clickExampleSubmissionsButton();
            await textExerciseExampleSubmissions.clickCreateExampleSubmission();
            await textExerciseExampleSubmissionCreation.showsExerciseTitle(exerciseTitle);
            await textExerciseExampleSubmissionCreation.showsProblemStatement(problemStatement);
            await textExerciseExampleSubmissionCreation.showsExampleSolution(exampleSolution);
            const submission = 'This is an\nexample\nsubmission';
            await textExerciseExampleSubmissionCreation.typeExampleSubmission(submission);

            const submissionCreationResponse = await textExerciseExampleSubmissionCreation.clickCreateNewExampleSubmission();
            const exampleSubmission: ExampleSubmission = await readResponseJson(submissionCreationResponse);
            const textSubmission: TextSubmission = exampleSubmission.submission!;
            expect(submissionCreationResponse.status()).toBe(200);
            expect(textSubmission.text).toBe(submission);

            // Make sure text exercise is shown in exercises list. Under heavy parallel CI
            // load the exercises-list response occasionally lags behind the create response,
            // so the freshly created card isn't in the first render. Reload up to three
            // times before failing — each reload re-fetches the list, and the server-side
            // commit reliably propagates within a few seconds.
            const exerciseUrl = `/course-management/${course.id}/exercises`;
            const card = courseManagementExercises.getExercise(exercise.id!);
            const visibleWithin = async (timeout: number): Promise<boolean> =>
                card
                    .waitFor({ state: 'visible', timeout })
                    .then(() => true)
                    .catch(() => false);
            await page.goto(exerciseUrl);
            await page.waitForLoadState('load');
            for (let attempt = 0; attempt < 3; attempt++) {
                if (await visibleWithin(20_000)) {
                    break;
                }
                if (attempt === 2) {
                    throw new Error(`Newly created text exercise card #exercise-card-${exercise.id!} did not appear after 3 reloads`);
                }
                await page.reload();
                await page.waitForLoadState('load');
            }
        });

        test.afterEach('Delete created exercise', async ({ login, exerciseAPIRequests }) => {
            if (createdExerciseId) {
                await login(admin);
                await exerciseAPIRequests.deleteTextExercise(createdExerciseId);
                createdExerciseId = undefined;
            }
        });
    });

    test.describe('Text exercise deletion', () => {
        let exercise: TextExercise;

        test.beforeEach('Create text exercise', async ({ login, exerciseAPIRequests }) => {
            await login(admin, '/');
            exercise = await exerciseAPIRequests.createTextExercise({ course });
        });

        test('Deletes an existing text exercise', async ({ login, navigationBar, courseManagement, courseManagementExercises }) => {
            await login(admin, '/');
            await navigationBar.openCourseManagement();
            await courseManagement.openExercisesOfCourse(course.id!);
            await courseManagementExercises.deleteTextExercise(exercise);
            await expect(courseManagementExercises.getExercise(exercise.id!)).not.toBeAttached();
        });
    });

    test.describe('Responsive exercise layout', () => {
        let exercise: TextExercise;

        test.beforeEach(async ({ login, exerciseAPIRequests }) => {
            await login(admin, '/');
            exercise = await exerciseAPIRequests.createTextExercise({ course });
        });

        test.afterEach(async ({ exerciseAPIRequests }) => {
            if (exercise?.id) {
                await exerciseAPIRequests.deleteTextExercise(exercise.id);
            }
        });

        for (const language of ['en', 'de'] as const) {
            test(`keeps the complete plagiarism response label, help and input usable in ${language}`, async ({ page }, testInfo) => {
                await usePageLanguage(page, language);
                await page.goto(`/course-management/${course.id}/text-exercises/${exercise.id}/edit`);
                await expect(page.locator('html')).toHaveAttribute('lang', language);
                const showControl = language === 'en' ? 'Show Continuous Plagiarism Control' : 'Zeige Kontinuierliche Plagiatskontrolle';
                await page.getByRole('button', { name: showControl, exact: true }).click();
                await page
                    .getByRole('checkbox', { name: language === 'en' ? 'Enable Continuous Plagiarism Control' : 'Aktiviere Kontinuierliche Plagiatskontrolle', exact: true })
                    .check();
                const field = page.getByTestId('plagiarism-response-period-field');
                const settings = page.getByTestId('plagiarism-control-settings');
                const toggle = page.getByTestId('plagiarism-control-toggle');
                const label = page.getByTestId('plagiarism-response-period-label');
                const help = page.getByTestId('plagiarism-response-period-help');
                const input = field.getByRole('spinbutton');
                await expect(input).toBeEnabled();
                await expect(input).toHaveAttribute('min', '7');
                await expect(input).toHaveAttribute('max', '31');

                for (const width of responsiveWidths) {
                    await test.step(`Complete CPC field at ${width}px`, async () => {
                        await page.setViewportSize({ width, height: 900 });
                        await input.scrollIntoViewIfNeeded();
                        await measure(field);
                        await expectFullTextInside(label, `${language} response period label at ${width}px`);
                        await expectInside(label, field, undefined, { name: 'response period label' });
                        await expectInside(help, page.getByTestId('plagiarism-response-period-label-row'), undefined, { name: 'response period help icon' });
                        await expectInside(input, field, undefined, { name: 'response period input' });
                        await expectNoOverlap([label, help], { name: 'response period label and help icon' });
                        await expectFullTextInside(toggle, `${language} CPC toggle at ${width}px`);
                        await expectInside(toggle, settings, undefined, { name: 'CPC toggle' });
                        await expectInside(settings, page.locator('#course-body-container'), ['left', 'right'], { name: 'CPC settings in the course column' });
                        // The exercise timeline has its own 2px phone-width overflow; keep this contract on CPC.
                        await expectNoHorizontalOverflow(page, [settings, field], { name: 'CPC settings' });
                        await input.fill('14');
                        await expect(input).toHaveValue('14');
                    });
                }

                for (const value of ['7', '31']) {
                    await input.fill(value);
                    expect(await input.evaluate((node: HTMLInputElement) => node.validity.valid)).toBe(true);
                }
                await input.fill('6');
                expect(await input.evaluate((node: HTMLInputElement) => node.validity.rangeUnderflow)).toBe(true);
                await input.fill('32');
                expect(await input.evaluate((node: HTMLInputElement) => node.validity.rangeOverflow)).toBe(true);
                await input.fill('14');
                await input.blur();
                await page.setViewportSize({ width: 1516, height: 900 });
                await input.scrollIntoViewIfNeeded();
                await page.screenshot({ path: testInfo.outputPath(`plagiarism-response-period-${language}.png`) });
            });

            test(`keeps exercise statistics headings and rings inside their columns in ${language}`, async ({ page }, testInfo) => {
                await usePageLanguage(page, language);
                await page.goto(`/course-management/${course.id}/text-exercises/${exercise.id}/exercise-statistics`);
                await expect(page.getByRole('heading', { name: language === 'en' ? 'Average Score' : 'Durchschnittspunktzahl', exact: true })).toBeVisible();
                const cards = page.getByTestId('exercise-statistic-card');
                expect(await cards.count()).toBeGreaterThanOrEqual(2);

                for (const width of responsiveWidths) {
                    await test.step(`Statistics columns at ${width}px`, async () => {
                        await page.setViewportSize({ width, height: 900 });
                        for (const card of await cards.all()) {
                            const heading = card.getByTestId('exercise-statistic-heading');
                            const ring = card.getByTestId('exercise-statistic-ring');
                            await heading.scrollIntoViewIfNeeded();
                            await measure(card);
                            await expectFullTextInside(heading, `${language} statistics heading at ${width}px`);
                            await expectInside(heading, card, undefined, { name: 'statistics heading' });
                            await expectInside(ring, card, undefined, { name: 'statistics ring' });
                            await expectNoOverlap([heading, ring], { name: 'statistics heading and ring' });
                            const ringBox = await measure(ring);
                            expect(ringBox.width).toBeLessThanOrEqual(201);
                            expect(Math.abs(ringBox.width - ringBox.height)).toBeLessThanOrEqual(1);
                        }
                        // Lower bar-chart SVGs have a separate phone-width overflow. This contract measures the summary
                        // row and its cards, so those unrelated charts cannot conceal a heading or ring regression.
                        await expectNoHorizontalOverflow(page, [page.getByTestId('exercise-statistics-summary'), ...(await cards.all())], { name: 'exercise statistics summary' });
                    });
                }
                await page.setViewportSize({ width: 812, height: 900 });
                await cards.first().getByTestId('exercise-statistic-heading').scrollIntoViewIfNeeded();
                await page.screenshot({ path: testInfo.outputPath(`exercise-statistics-${language}.png`) });
            });
        }
    });

    // Seed courses are persistent — no cleanup needed
});
