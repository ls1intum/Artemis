import { test } from '../../support/fixtures';
import { admin, studentOne } from '../../support/users';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { expect } from '@playwright/test';
import dayjs from 'dayjs';
import { Buffer } from 'buffer';
import { SEED_COURSES } from '../../support/seedData';
import { generateUUID } from '../../support/utils';

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

            // Wait for the completion to be processed
            await page.waitForLoadState(WAIT_STATE);

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

        test('Exercise submission updates competency progress indicator', async ({
            page,
            login,
            courseManagementAPIRequests,
            exerciseAPIRequests,
            courseOverview,
            quizExerciseMultipleChoice,
        }) => {
            // Create competency first
            const competency = await courseManagementAPIRequests.createCompetency(nestedCourse, 'Progress Test Competency', 'Track progress');

            // Create quiz exercise with competency link using direct API call
            // Use a very short duration so the quiz ends quickly after submission
            const quizExerciseDTO = {
                title: 'Progress Test Quiz',
                releaseDate: dayjs().subtract(1, 'hour').toISOString(),
                startDate: null,
                dueDate: dayjs().add(1, 'day').toISOString(),
                difficulty: 'EASY',
                mode: 'INDIVIDUAL',
                includedInOverallScore: 'INCLUDED_COMPLETELY',
                competencyLinks: [{ competency: { id: competency.id, type: 'competency' }, weight: 1 }],
                categories: [],
                channelName: 'exercise-progress-test-quiz',
                randomizeQuestionOrder: false,
                quizMode: 'SYNCHRONIZED',
                // When startQuizNow is called, the server overrides dueDate to
                // now + duration + QUIZ_GRACE_PERIOD (5s). Results are calculated at
                // dueDate + 5s by QuizScheduleService. With duration=10, results are
                // calculated ~20s after quiz start. Programmatic participation (login,
                // navigate, tick, submit) takes ~5s, so 10s is sufficient.
                duration: 10,
                quizBatches: [{ startTime: dayjs().toISOString() }],
                quizQuestions: [
                    {
                        type: 'multiple-choice',
                        title: 'Test Question',
                        text: 'Which answers are correct?',
                        hint: 'Choose wisely',
                        scoringType: 'PROPORTIONAL_WITHOUT_PENALTY',
                        points: 10,
                        randomizeOrder: false,
                        singleChoice: false,
                        answerOptions: [
                            { isCorrect: true, text: 'Correct answer 1', hint: 'A hint', explanation: 'Correct' },
                            { isCorrect: true, text: 'Correct answer 2', hint: 'A hint', explanation: 'Correct' },
                            { isCorrect: false, text: 'Wrong answer 1', hint: 'A hint', explanation: 'Wrong' },
                            { isCorrect: false, text: 'Wrong answer 2', hint: 'A hint', explanation: 'Wrong' },
                        ],
                    },
                ],
            };

            const createResponse = await page.request.post(`api/quiz/courses/${nestedCourse.id}/quiz-exercises`, {
                multipart: {
                    exercise: {
                        name: 'exercise',
                        mimeType: 'application/json',
                        buffer: Buffer.from(JSON.stringify(quizExerciseDTO)),
                    },
                },
            });
            expect(createResponse.ok()).toBeTruthy();
            const quizExercise = await createResponse.json();

            // Make quiz visible and start it immediately
            const visibleResponse = await page.request.put(`api/quiz/quiz-exercises/${quizExercise.id}/set-visible`);
            expect(visibleResponse.ok(), `setQuizVisible failed: ${visibleResponse.status()}`).toBeTruthy();
            const startResponse = await page.request.put(`api/quiz/quiz-exercises/${quizExercise.id}/start-now`);
            expect(startResponse.ok(), `startQuizNow failed: ${startResponse.status()}`).toBeTruthy();

            // Login as student and navigate directly to quiz exercise
            await login(studentOne);
            await page.goto(`/courses/${nestedCourse.id}/exercises/${quizExercise.id}`);
            await page.waitForLoadState('networkidle');

            // Start the exercise
            await courseOverview.startExercise(quizExercise.id!);

            // Wait for quiz participation page to load with questions
            await page.waitForSelector(`#exercise-${quizExercise.id}`, { timeout: 10000 });
            await page.waitForSelector('#answer-option-0', { timeout: 10000 });

            // Answer the multiple choice question - tick the first two options (correct answers)
            await quizExerciseMultipleChoice.tickAnswerOption(quizExercise.id!, 0);
            await quizExerciseMultipleChoice.tickAnswerOption(quizExercise.id!, 1);

            // Submit the quiz
            const response = await quizExerciseMultipleChoice.submit();
            expect(response.status()).toBe(200);

            // Wait for the quiz to end naturally and for competency progress to be calculated
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
            const progressRings = page.locator('jhi-competency-rings').first();
            await expect(progressRings).toBeVisible();

            // Navigate to competency detail to verify progress has increased
            await page.goto(`/courses/${nestedCourse.id}/competencies/${competency.id}`);
            await page.waitForLoadState('domcontentloaded');

            // Check that the exercise shows in the competency detail
            await expect(page.getByRole('heading', { name: 'Progress Test Quiz' })).toBeVisible();

            // Verify the progress ring is visible indicating progress tracking is working
            await expect(page.locator('jhi-competency-rings')).toBeVisible();
        });
    });
});
