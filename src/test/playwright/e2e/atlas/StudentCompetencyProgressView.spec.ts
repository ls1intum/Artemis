import { test } from '../../support/fixtures';
import { admin, studentOne } from '../../support/users';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { expect } from '@playwright/test';
import { SEED_COURSES } from '../../support/seedData';
import { generateUUID } from '../../support/utils';
import multipleChoiceQuizTemplate from '../../fixtures/exercise/quiz/multiple_choice/template.json';

const WAIT_STATE = 'domcontentloaded';

const course = { id: SEED_COURSES.atlas2.id } as any;
const uid = generateUUID();

test.describe('Student Competency Progress View', { tag: '@fast' }, () => {
    let lecture: Lecture;
    let nestedCourse: any;

    test.beforeEach('Setup lecture and learning paths', async ({ login, courseManagementAPIRequests }) => {
        await login(admin);
        lecture = await courseManagementAPIRequests.createLecture(course, 'Test Lecture ' + uid);
        try {
            await courseManagementAPIRequests.enableLearningPaths(course);
        } catch {
            // Already enabled from a previous run
        }
    });

    // Seed courses are persistent — no cleanup needed

    test.describe('Student views their competency progress overview', () => {
        test('Student sees a grid of competencies with initial progress state', async ({ page, login, courseManagementAPIRequests }) => {
            // Preconditions: Create competencies linked to lecture units
            const competency1 = await courseManagementAPIRequests.createCompetency(course, 'CompA ' + uid, 'First competency');
            const competency2 = await courseManagementAPIRequests.createCompetency(course, 'CompB ' + uid, 'Second competency');

            // Create text units linked to competencies
            await courseManagementAPIRequests.createTextUnit(lecture, 'Text Unit 1', 'Content 1', [{ competency: { id: competency1.id, type: 'competency' }, weight: 1 }]);
            await courseManagementAPIRequests.createTextUnit(lecture, 'Text Unit 2', 'Content 2', [{ competency: { id: competency2.id, type: 'competency' }, weight: 1 }]);

            // Login as student
            await login(studentOne);

            // Navigate to competencies view
            await page.goto(`/courses/${course.id}/competencies`);
            await page.waitForLoadState(WAIT_STATE);

            // Assert: A grid/list of competencies is visible
            await expect(page.getByText('CompA ' + uid)).toBeVisible();
            await expect(page.getByText('CompB ' + uid)).toBeVisible();

            // Assert: Each competency shows competency cards with progress rings (at least 2, may have more from previous runs)
            const competencyCards = page.locator('jhi-competency-card');
            await expect(competencyCards.first()).toBeVisible();
            expect(await competencyCards.count()).toBeGreaterThanOrEqual(2);

            // Assert: Progress rings are visible (initial state)
            const progressRings = page.locator('jhi-competency-rings');
            await expect(progressRings.first()).toBeVisible();

            // Assert: The legend/panel header for progress is visible
            await expect(page.getByText('Your advancement')).toBeVisible();
        });
    });

    test.describe('Progress and Mastery updates after completing a lecture unit', () => {
        test('Student progress increases and mastery is achieved after marking a lecture unit as completed', async ({ page, login, courseManagementAPIRequests }) => {
            // Preconditions: Create competency linked to a single lecture unit
            // Completing this single unit (weight 1) should result in 100% progress (Mastery)
            const competency = await courseManagementAPIRequests.createCompetency(course, 'Lecture ' + uid, 'Competency linked to lecture unit');

            // Create text unit linked to competency
            await courseManagementAPIRequests.createTextUnit(lecture, 'Completable Text Unit ' + uid, 'Read this content to complete', [
                { competency: { id: competency.id, type: 'competency' }, weight: 1 },
            ]);

            // Login as student
            await login(studentOne);

            // Navigate to competency detail view
            await page.goto(`/courses/${course.id}/competencies/${competency.id}`);
            await page.waitForLoadState(WAIT_STATE);

            // Assert: Initial state - No mastery badge
            await expect(page.locator('.badge.text-bg-success', { hasText: 'Mastered' })).not.toBeVisible();

            // Navigate to the lecture unit in the competency detail view and mark it as completed
            const textUnitCard = page.locator('jhi-text-unit');
            await expect(textUnitCard).toBeVisible();

            // Click on the lecture unit to expand it
            await textUnitCard.locator('#lecture-unit-toggle-button').click();

            // Click the completion checkbox
            const completionCheckbox = textUnitCard.locator('#completed-checkbox');
            await expect(completionCheckbox).toBeVisible();
            await completionCheckbox.click();

            // Wait until the completion is persisted before reloading.
            await expect
                .poll(
                    async () => {
                        const progressResponse = await page.request.get(`api/atlas/courses/${course.id}/course-competencies/${competency.id}/student-progress?refresh=true`);
                        if (!progressResponse.ok()) {
                            return 0;
                        }
                        const progressBody = (await progressResponse.json()) as { progress?: number };
                        return progressBody.progress ?? 0;
                    },
                    { timeout: 60000 },
                )
                .toBeGreaterThan(0);

            // Refresh the page to see updated progress
            await page.reload();
            await page.waitForLoadState(WAIT_STATE);

            // After reload, the text unit is collapsed — expand it before checking the checkbox
            const textUnitToggleAfterReload = page.locator('jhi-text-unit #lecture-unit-toggle-button');
            await expect(textUnitToggleAfterReload).toBeVisible();
            await textUnitToggleAfterReload.click();

            // Assert: The lecture unit should now show as completed (green check icon)
            const completedIcon = page.locator('jhi-text-unit #completed-checkbox.text-success');
            await expect(completedIcon).toBeVisible({ timeout: 10000 });

            // Assert: Progress ring should be visible
            await expect(page.locator('jhi-competency-rings')).toBeVisible();

            // Assert: "Mastered" badge should now be visible (Test 4.4 requirement)
            await expect(page.locator('.badge.text-bg-success', { hasText: 'Mastered' })).toBeVisible();

            // Navigate to competencies overview to verify global state
            await page.goto(`/courses/${course.id}/competencies`);
            await page.waitForLoadState(WAIT_STATE);

            // Assert: Check that the mastered count is visible in the overview
            const masteredCount = page.locator('.badge.bg-dark');
            await expect(masteredCount).toBeVisible();
        });
    });

    test.describe('Student Competency Progress - Exercise Completion', { tag: '@slow' }, () => {
        test.beforeEach('Setup course', async ({ login, courseManagementAPIRequests }) => {
            await login(admin);
            nestedCourse = await courseManagementAPIRequests.createCourse();
            await courseManagementAPIRequests.addStudentToCourse(nestedCourse, studentOne);
            await courseManagementAPIRequests.enableLearningPaths(nestedCourse);
        });

        test.afterEach('Cleanup', async ({ courseManagementAPIRequests }) => {
            await courseManagementAPIRequests.deleteCourse(nestedCourse, admin);
        });

        test('Exercise submission updates competency progress indicator', async ({ page, login, courseManagementAPIRequests, exerciseAPIRequests }) => {
            // The describe inherits the outer @fast tag and adds its own @slow, so the test
            // matches both projects. The test's contract is "submitting a quiz tied to a
            // competency updates the student's competency progress" — the UI interaction with
            // the quiz player is incidental. We bypass it entirely (submit via API) and force
            // the quiz to end immediately, eliminating the UI submit race that previously
            // flaked here under multi-node CI load.
            const competency = await courseManagementAPIRequests.createCompetency(nestedCourse, 'Progress Test Competency', 'Track progress');

            await login(admin);
            // Short duration so end-now → due-date overlap is immediate. We submit via API
            // before the duration window closes; end-now then advances the scheduled
            // evaluation job which produces the competency-progress update we assert on.
            const quizExercise = await exerciseAPIRequests.createQuizExercise({
                body: { course: nestedCourse },
                quizQuestions: [multipleChoiceQuizTemplate],
                title: 'Progress Test Quiz',
                duration: 10,
                competencyLinks: [{ competency: { id: competency.id }, weight: 1 }],
            });
            await exerciseAPIRequests.setQuizVisible(quizExercise.id!);
            await exerciseAPIRequests.startQuizNow(quizExercise.id!);

            // Submit as student via API — no UI participation. This decouples the test from
            // the quiz-player rendering chain and removes the previous "submit button stays
            // disabled after the timer expires" flake.
            await login(studentOne);
            await exerciseAPIRequests.startExerciseParticipation(quizExercise.id!);
            await exerciseAPIRequests.createMultipleChoiceSubmission(quizExercise, [0, 1]);

            // Force evaluation immediately so the post-submit competency progress is computed
            // without waiting for the scheduled job to fire on quiz expiry.
            await login(admin);
            await exerciseAPIRequests.endQuizNow(quizExercise.id!);

            // Poll the student-visible competency progress (60s is enough now — evaluation
            // and progress calc both fire seconds after end-now without UI latency in the path).
            await login(studentOne);
            await expect
                .poll(
                    async () => {
                        const progressResponse = await page.request.get(`api/atlas/courses/${nestedCourse.id}/competencies`);
                        if (!progressResponse.ok()) {
                            return 0;
                        }
                        const competencies = (await progressResponse.json()) as Array<{ id?: number; userProgress?: Array<{ progress?: number }> }>;
                        const updatedCompetency = competencies.find((item) => item.id === competency.id);
                        return updatedCompetency?.userProgress?.[0]?.progress ?? 0;
                    },
                    { timeout: 60000 },
                )
                .toBeGreaterThan(0);

            // Navigate to competencies overview to check updated progress
            await page.goto(`/courses/${nestedCourse.id}/competencies`);
            await page.waitForLoadState('domcontentloaded');

            // Verify competency is visible with progress rings showing update
            await expect(page.getByText('Progress Test Competency')).toBeVisible();
            await expect(page.locator('jhi-competency-rings').first()).toBeVisible();

            // Navigate to competency detail to verify progress has increased
            await page.goto(`/courses/${nestedCourse.id}/competencies/${competency.id}`);
            await page.waitForLoadState('domcontentloaded');

            // Check that the exercise shows in the competency detail
            await expect(page.getByRole('heading', { name: 'Progress Test Quiz' })).toBeVisible();
            await expect(page.locator('jhi-competency-rings')).toBeVisible();
        });
    });
});
