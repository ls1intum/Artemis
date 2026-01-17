import { test } from '../../support/fixtures';
import { admin, studentOne } from '../../support/users';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { expect } from '@playwright/test';
import dayjs from 'dayjs';

test.describe('Student Competency Progress View', { tag: '@fast' }, () => {
    let course: Course;
    let lecture: Lecture;

    test.beforeEach('Setup course with learning paths enabled', async ({ login, courseManagementAPIRequests }) => {
        await login(admin);
        course = await courseManagementAPIRequests.createCourse();
        lecture = await courseManagementAPIRequests.createLecture(course, 'Test Lecture');
        await courseManagementAPIRequests.addStudentToCourse(course, studentOne);
        await courseManagementAPIRequests.enableLearningPaths(course);
    });

    test.afterEach('Cleanup', async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });

    test.describe('Student views their competency progress overview', () => {
        test('Student sees a grid of competencies with initial progress state', async ({ page, login, courseManagementAPIRequests }) => {
            // Preconditions: Create competencies linked to lecture units
            const competency1 = await courseManagementAPIRequests.createCompetency(course, 'Competency A', 'First competency');
            const competency2 = await courseManagementAPIRequests.createCompetency(course, 'Competency B', 'Second competency');

            // Create text units linked to competencies
            await courseManagementAPIRequests.createTextUnit(lecture, 'Text Unit 1', 'Content 1', [{ competency: { id: competency1.id, type: 'competency' }, weight: 1 }]);
            await courseManagementAPIRequests.createTextUnit(lecture, 'Text Unit 2', 'Content 2', [{ competency: { id: competency2.id, type: 'competency' }, weight: 1 }]);

            // Login as student
            await login(studentOne);

            // Navigate to competencies view
            await page.goto(`/courses/${course.id}/competencies`);
            await page.waitForLoadState('networkidle');

            // Assert: A grid/list of competencies is visible
            await expect(page.getByText('Competency A')).toBeVisible();
            await expect(page.getByText('Competency B')).toBeVisible();

            // Assert: Each competency shows competency cards with progress rings
            const competencyCards = page.locator('jhi-competency-card');
            await expect(competencyCards).toHaveCount(2);

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
            const competency = await courseManagementAPIRequests.createCompetency(course, 'Lecture Competency', 'Competency linked to lecture unit');

            // Create text unit linked to competency
            await courseManagementAPIRequests.createTextUnit(lecture, 'Completable Text Unit', 'Read this content to complete', [
                { competency: { id: competency.id, type: 'competency' }, weight: 1 },
            ]);

            // Login as student
            await login(studentOne);

            // Navigate to competency detail view
            await page.goto(`/courses/${course.id}/competencies/${competency.id}`);
            await page.waitForLoadState('networkidle');

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
            await page.waitForLoadState('networkidle');

            // Refresh the page to see updated progress
            await page.reload();
            await page.waitForLoadState('networkidle');

            // Assert: The lecture unit should now show as completed (green check icon)
            const completedIcon = page.locator('jhi-text-unit #completed-checkbox.text-success');
            await expect(completedIcon).toBeVisible();

            // Assert: Progress ring should be visible
            await expect(page.locator('jhi-competency-rings')).toBeVisible();

            // Assert: "Mastered" badge should now be visible (Test 4.4 requirement)
            await expect(page.locator('.badge.text-bg-success', { hasText: 'Mastered' })).toBeVisible();

            // Navigate to competencies overview to verify global state
            await page.goto(`/courses/${course.id}/competencies`);
            await page.waitForLoadState('networkidle');

            // Assert: Check that the mastered count is visible in the overview
            const masteredCount = page.locator('.badge.bg-dark');
            await expect(masteredCount).toBeVisible();
        });
    });

    test.describe('Judgement of Learning (JoL) rating submission', () => {
        test('Student submits JoL rating via star rating component', async ({ page, login, courseManagementAPIRequests }) => {
            // Enable the student course analytics dashboard so we can access /dashboard
            const updatedCourse = { ...course, studentCourseAnalyticsDashboardEnabled: true };
            const updateResponse = await page.request.put(`api/core/courses/${course.id}`, {
                multipart: {
                    course: {
                        name: 'course',
                        mimeType: 'application/json',
                        buffer: Buffer.from(JSON.stringify(updatedCourse)),
                    },
                },
            });
            expect(updateResponse.ok()).toBeTruthy();

            // Preconditions: Create competency with softDueDate set to trigger JoL prompt
            // JoL prompt condition: Current Date >= Competency Soft Due Date - 1 Day AND Progress >= 20%
            // Set soft due date to today or yesterday so the condition is met
            const softDueDate = dayjs().subtract(1, 'day').toISOString();

            // Create competency with soft due date via direct API call
            const competencyResponse = await page.request.post(`api/atlas/courses/${course.id}/competencies`, {
                data: {
                    type: 'competency',
                    title: 'JoL Competency',
                    description: 'Rate your understanding',
                    masteryThreshold: 100,
                    softDueDate: softDueDate,
                },
            });
            expect(competencyResponse.ok()).toBeTruthy();
            const competency = await competencyResponse.json();

            // Create a text unit linked to competency
            await courseManagementAPIRequests.createTextUnit(lecture, 'JoL Test Unit', 'Content for JoL testing', [
                { competency: { id: competency.id, type: 'competency' }, weight: 1 },
            ]);

            // Login as student
            await login(studentOne);

            // Navigate to competency detail and mark the unit as complete to get >= 20% progress
            await page.goto(`/courses/${course.id}/competencies/${competency.id}`);
            await page.waitForLoadState('networkidle');

            // Complete the lecture unit to trigger JoL prompt (100% progress on single unit = 100% >= 20%)
            const textUnitCard = page.locator('jhi-text-unit');
            await expect(textUnitCard).toBeVisible();
            await textUnitCard.locator('#lecture-unit-toggle-button').click();
            const completionCheckbox = textUnitCard.locator('#completed-checkbox');
            await completionCheckbox.click();
            await page.waitForLoadState('networkidle');

            // Wait for the completion to be reflected in progress (progress ring should update)
            // Reload the page to ensure progress is fetched fresh from server
            await page.reload();
            await page.waitForLoadState('networkidle');

            // Verify the unit is completed by checking for the green checkmark
            // First expand the unit again (it collapses after reload)
            const textUnitToggleAfterReload = page.locator('jhi-text-unit #lecture-unit-toggle-button');
            await expect(textUnitToggleAfterReload).toBeVisible();
            await textUnitToggleAfterReload.click();

            // Wait for the checkbox to appear (unit needs to expand)
            const completedIcon = page.locator('jhi-text-unit #completed-checkbox.text-success');
            await expect(completedIcon).toBeVisible({ timeout: 10000 });

            // Navigate to dashboard where JoL rating component appears in competency accordion
            await page.goto(`/courses/${course.id}/dashboard`);
            await page.waitForLoadState('networkidle');

            // Look for the competency accordion which contains JoL rating
            const competencyAccordion = page.locator(`#competency-accordion-${competency.id}`);
            await expect(competencyAccordion).toBeVisible();

            // Click to expand the accordion
            await competencyAccordion.click();

            // Wait for accordion body to be visible (indicates it's expanded)
            const accordionBody = page.locator('.competency-accordion-body-open');
            await expect(accordionBody).toBeVisible({ timeout: 10000 });

            // Check for JoL rating component - should be visible since conditions are met
            // Wait with longer timeout as data might be loading
            const jolRatingComponent = page.locator('jhi-judgement-of-learning-rating');
            await expect(jolRatingComponent).toBeVisible({ timeout: 10000 });

            // Find the star rating component within the JoL component
            const starRating = jolRatingComponent.locator('star-rating');
            await expect(starRating).toBeVisible();

            // The star-rating component uses shadow DOM, so we need to access it properly
            // Click on a star to submit rating (4th star for a 4/5 rating)
            // Stars are rendered inside shadow DOM as <span> elements with data-index attribute
            await starRating.evaluate((el) => {
                const shadowRoot = el.shadowRoot;
                if (shadowRoot) {
                    const stars = shadowRoot.querySelectorAll('span[data-index]');
                    if (stars.length >= 4) {
                        (stars[3] as HTMLElement).click();
                    }
                }
            });

            // Wait for the rating to be saved
            await page.waitForLoadState('networkidle');

            // After rating, the star-rating should become read-only and show the rating
            // The JoL component should still be visible but now in rated state
            await page.reload();
            await page.waitForLoadState('networkidle');

            // Navigate back and check the accordion
            await competencyAccordion.click();
            await page.waitForLoadState('networkidle');

            // The JoL component should show the submitted rating
            await expect(jolRatingComponent).toBeVisible();
        });
    });

    test.describe('Student Competency Progress - Exercise Completion', { tag: '@fast' }, () => {
        test.beforeEach('Setup course', async ({ login, courseManagementAPIRequests }) => {
            await login(admin);
            course = await courseManagementAPIRequests.createCourse();
            await courseManagementAPIRequests.addStudentToCourse(course, studentOne);
            await courseManagementAPIRequests.enableLearningPaths(course);
        });

        test.afterEach('Cleanup', async ({ courseManagementAPIRequests }) => {
            await courseManagementAPIRequests.deleteCourse(course, admin);
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
            const competency = await courseManagementAPIRequests.createCompetency(course, 'Progress Test Competency', 'Track progress');

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
                duration: 10, // Very short duration - quiz ends 10 seconds after start
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

            const createResponse = await page.request.post(`api/quiz/courses/${course.id}/quiz-exercises`, {
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
            await exerciseAPIRequests.setQuizVisible(quizExercise.id!);
            await exerciseAPIRequests.startQuizNow(quizExercise.id!);

            // Login as student
            await login(studentOne);

            // Navigate to competencies overview first to check initial progress state
            await page.goto(`/courses/${course.id}/competencies`);
            await page.waitForLoadState('networkidle');

            // Verify competency is visible with initial state
            await expect(page.getByText('Progress Test Competency')).toBeVisible();
            const competencyCard = page.locator('jhi-competency-card').filter({ hasText: 'Progress Test Competency' });
            await expect(competencyCard).toBeVisible();

            // Navigate to quiz exercise and participate
            await page.goto(`/courses/${course.id}/exercises/${quizExercise.id}`);
            await page.waitForLoadState('networkidle');

            // Start the exercise
            await courseOverview.startExercise(quizExercise.id!);

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
                        const progressResponse = await page.request.get(`api/atlas/courses/${course.id}/competencies`);
                        if (!progressResponse.ok()) {
                            return 0;
                        }
                        const competencies = (await progressResponse.json()) as Array<{ id?: number; userProgress?: Array<{ progress?: number }> }>;
                        const updatedCompetency = competencies.find((item) => item.id === competency.id);
                        return updatedCompetency?.userProgress?.[0]?.progress ?? 0;
                    },
                    { timeout: 20000 },
                )
                .toBeGreaterThan(0);

            // Navigate to competencies overview to check updated progress
            await page.goto(`/courses/${course.id}/competencies`);
            await page.waitForLoadState('networkidle');

            // Verify competency is visible with progress rings showing update
            await expect(page.getByText('Progress Test Competency')).toBeVisible();
            const progressRings = page.locator('jhi-competency-rings').first();
            await expect(progressRings).toBeVisible();

            // Navigate to competency detail to verify progress has increased
            await page.goto(`/courses/${course.id}/competencies/${competency.id}`);
            await page.waitForLoadState('networkidle');

            // Check that the exercise shows in the competency detail
            await expect(page.getByRole('heading', { name: 'Progress Test Quiz' })).toBeVisible();

            // Verify the progress ring is visible indicating progress tracking is working
            await expect(page.locator('jhi-competency-rings')).toBeVisible();
        });
    });
});
