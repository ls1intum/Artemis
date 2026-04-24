import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import multipleChoiceQuizTemplate from '../../../fixtures/exercise/quiz/multiple_choice/template.json';
import shortAnswerQuizTemplate from '../../../fixtures/exercise/quiz/short_answer/template.json';
import { admin, instructor, studentOne } from '../../../support/users';
import { test } from '../../../support/fixtures';
import { expect } from '@playwright/test';
import dayjs from 'dayjs';
import { QuizMode } from '../../../support/constants';
import { SEED_COURSES } from '../../../support/seedData';

const course = { id: SEED_COURSES.quizParticipation.id } as any;

test.describe('Quiz Exercise Participation', { tag: '@fast' }, () => {
    test.describe('Quiz exercise participation', () => {
        let quizExercise: QuizExercise;

        test.beforeEach('Create quiz exercise', async ({ login, exerciseAPIRequests }) => {
            await login(admin);
            quizExercise = await exerciseAPIRequests.createQuizExercise({ body: { course }, quizQuestions: [multipleChoiceQuizTemplate] });
        });

        test('Student cannot see hidden quiz', async ({ login, courseOverview }) => {
            await login(studentOne, '/courses/' + course.id);
            await expect(courseOverview.getOpenRunningExerciseButton(quizExercise.id!)).not.toBeVisible();
        });

        test('Student can see a visible quiz', async ({ login, exerciseAPIRequests, courseOverview }) => {
            await login(admin);
            await exerciseAPIRequests.setQuizVisible(quizExercise.id!);
            await login(studentOne, `/courses/${course.id}/exercises/${quizExercise.id!}`);
            await courseOverview.shouldShowExerciseTitleInHeader(quizExercise.title!);
        });

        test('Student can participate in MC quiz', async ({ login, exerciseAPIRequests, courseOverview, quizExerciseMultipleChoice }) => {
            await login(admin);
            await exerciseAPIRequests.setQuizVisible(quizExercise.id!);
            await exerciseAPIRequests.startQuizNow(quizExercise.id!);
            await login(studentOne, `/courses/${course.id}/exercises/${quizExercise.id!}`);
            await quizExerciseMultipleChoice.tickAnswerOption(quizExercise.id!, 0);
            await quizExerciseMultipleChoice.tickAnswerOption(quizExercise.id!, 2);
            await quizExerciseMultipleChoice.submit();
        });

        /**
         * Regression test for https://github.com/ls1intum/Artemis/issues/12574: after ending and evaluating an MC quiz,
         * every refresh of the participation page must return the complete set of answer options the student selected. Previously,
         * Hibernate's EAGER fetch of MultipleChoiceSubmittedAnswer.selectedOptions (combined with second-level caching) could
         * yield partial collections, so options appeared deselected and the per-question score flipped between 0 and its true value.
         */
        test('Selected MC options stay fully populated across reloads after quiz evaluation', async ({ login, exerciseAPIRequests, page, quizExerciseMultipleChoice }) => {
            const quizDurationSeconds = 10;
            await login(admin);
            const shortQuiz = await exerciseAPIRequests.createQuizExercise({
                body: { course },
                quizQuestions: [multipleChoiceQuizTemplate],
                duration: quizDurationSeconds,
            });
            await exerciseAPIRequests.setQuizVisible(shortQuiz.id!);
            await exerciseAPIRequests.startQuizNow(shortQuiz.id!);

            // Pick answer-option indices to tick explicitly — the assertion below must compare against what the student ticked, not against the (unrelated) `isCorrect` property.
            const tickedOptionIndices = [0, 1];
            await login(studentOne, `/courses/${course.id}/exercises/${shortQuiz.id!}`);
            for (const index of tickedOptionIndices) {
                await quizExerciseMultipleChoice.tickAnswerOption(shortQuiz.id!, index);
            }
            await quizExerciseMultipleChoice.submit();

            const mcQuestionId = shortQuiz.quizQuestions![0].id!;
            const expectedTickedOptionIds = tickedOptionIndices.map((index) => shortQuiz.quizQuestions![0].answerOptions![index].id);
            expect(expectedTickedOptionIds).toHaveLength(tickedOptionIndices.length);

            /**
             * Reload the participation page and read the server's response to `/start-participation`. Returns the set of selected option ids for
             * the MC question on this response, or null when evaluation has not yet populated a rated result (e.g. the `results` array is still empty).
             */
            async function reloadAndReadSelectedOptionIds(): Promise<number[] | null> {
                const responsePromise = page.waitForResponse(
                    (response) =>
                        response.url().includes(`/api/quiz/quiz-exercises/${shortQuiz.id}/start-participation`) && response.request().method() === 'POST' && response.ok(),
                );
                await page.goto(`/courses/${course.id}/exercises/${shortQuiz.id!}`);
                const body = await (await responsePromise).json();
                const submission = (body.submissions ?? [])[0];
                if (!submission || !(submission.results ?? []).length) {
                    return null;
                }
                const mcAnswer = (submission.submittedAnswers ?? []).find((submittedAnswer: any) => submittedAnswer.quizQuestion?.id === mcQuestionId);
                return mcAnswer ? (mcAnswer.selectedOptions ?? []).map((option: any) => option.id).sort((a: number, b: number) => a - b) : [];
            }

            // Poll the refresh endpoint until the scheduled evaluation job has created a rated result — more robust than a fixed sleep on loaded CI workers.
            const evaluationTimeoutMs = (quizDurationSeconds + 30) * 1000;
            await expect
                .poll(() => reloadAndReadSelectedOptionIds().then((ids) => ids !== null), {
                    message: 'evaluation did not produce a rated result within the expected window',
                    timeout: evaluationTimeoutMs,
                    intervals: [1000, 2000, 3000],
                })
                .toBe(true);

            const expectedSortedOptionIds = [...expectedTickedOptionIds].sort((a, b) => a - b);
            // Reload several times after evaluation completes; the bug manifested non-deterministically, so the loop amplifies any remaining flakiness.
            for (let iteration = 0; iteration < 5; iteration++) {
                const selectedOptionIds = await reloadAndReadSelectedOptionIds();
                expect(selectedOptionIds, `iteration ${iteration}: server must return exactly the answer options the student ticked`).toEqual(expectedSortedOptionIds);
            }
        });

        /**
         * Regression test for https://github.com/ls1intum/Artemis/issues/12584: clicking "Set visible" / "Start now"
         * on a quiz must not regenerate the primary keys of the child rows (answer options, drag items, drop
         * locations, short-answer spots). Before the fix, the REST handler called {@code saveAndFlush} on the
         * eagerly-loaded quiz graph and the unowned {@code @OneToMany + @OrderColumn + orphanRemoval} child
         * collections were DELETE+INSERTed with fresh auto-generated IDs, so any in-flight student submit carrying
         * the old IDs produced an {@code ObjectNotFoundException}. This test pins the fix by snapshotting the MC
         * answer-option IDs before and after each lifecycle action and asserting equality.
         */
        test('Lifecycle actions preserve answer-option IDs', async ({ login, exerciseAPIRequests, page }) => {
            await login(admin);
            const createdQuiz = await exerciseAPIRequests.createQuizExercise({
                body: { course },
                quizQuestions: [multipleChoiceQuizTemplate],
                releaseDate: dayjs().add(1, 'hour'),
            });

            // Capture the IDs that the client will send back on submit.
            const initialOptionIds = (createdQuiz.quizQuestions![0] as any).answerOptions!.map((opt: any) => opt.id).sort((a: number, b: number) => a - b);
            expect(initialOptionIds.length).toBeGreaterThan(0);

            const readOptionIdsFromServer = async (): Promise<number[]> => {
                const response = await page.request.get(`/api/quiz/quiz-exercises/${createdQuiz.id}`);
                expect(response.ok()).toBeTruthy();
                const body = await response.json();
                return (body.quizQuestions ?? [])
                    .filter((q: any) => q.type === 'multiple-choice' || (q.answerOptions ?? []).length > 0)
                    .flatMap((q: any) => (q.answerOptions ?? []).map((opt: any) => opt.id))
                    .sort((a: number, b: number) => a - b);
            };

            // Sanity check: IDs in the creation response match what the server now holds.
            expect(await readOptionIdsFromServer()).toEqual(initialOptionIds);

            await exerciseAPIRequests.setQuizVisible(createdQuiz.id!);
            expect(await readOptionIdsFromServer(), 'SET_VISIBLE must not regenerate AnswerOption ids').toEqual(initialOptionIds);

            await exerciseAPIRequests.startQuizNow(createdQuiz.id!);
            expect(await readOptionIdsFromServer(), 'START_NOW must not regenerate AnswerOption ids').toEqual(initialOptionIds);
        });
    });

    test.describe('Quiz exercise scheduled participation', () => {
        let quizExercise: QuizExercise;
        const timeUntilQuizStartInSeconds = 15;

        test.beforeEach('Create quiz exercise', async ({ login, exerciseAPIRequests }) => {
            await login(admin);
            const releaseDate = dayjs();
            const startOfWorkingTime = releaseDate.add(timeUntilQuizStartInSeconds, 'seconds');
            quizExercise = await exerciseAPIRequests.createQuizExercise({ body: { course }, quizQuestions: [multipleChoiceQuizTemplate], releaseDate, startOfWorkingTime });
        });

        test('Student cannot participate in scheduled quiz before start of working time', async ({ login, courseOverview, quizExerciseParticipation }) => {
            await login(studentOne, `/courses/${course.id}/exercises/${quizExercise.id}`);
            await expect(quizExerciseParticipation.getWaitingForStartAlert()).toBeVisible();
        });

        test('Student can participate in scheduled quiz when working time arrives', async ({ page, login, courseOverview, quizExerciseParticipation }) => {
            await login(studentOne, `/courses/${course.id}/exercises/${quizExercise.id}`);
            await page.waitForTimeout(timeUntilQuizStartInSeconds * 1000 + 3000);
            await expect(quizExerciseParticipation.getWaitingForStartAlert()).not.toBeVisible({ timeout: 10000 });
            await expect(quizExerciseParticipation.getQuizQuestion(0)).toBeVisible();
        });
    });

    test.describe('Quiz exercise batched participation', () => {
        let quizExercise: QuizExercise;
        const exerciseDuration = 60;

        test.beforeEach('Create quiz exercise', async ({ login, exerciseAPIRequests, courseManagementAPIRequests }) => {
            await login(admin);
            quizExercise = await exerciseAPIRequests.createQuizExercise({
                body: { course },
                quizQuestions: [multipleChoiceQuizTemplate],
                releaseDate: dayjs(),
                duration: exerciseDuration,
                quizMode: QuizMode.BATCHED,
            });
            await courseManagementAPIRequests.addInstructorToCourse(course, instructor);
        });

        test('Instructor creates a quiz batch and student joins it', async ({
            login,
            navigationBar,
            courseManagement,
            quizExerciseOverview,
            courseOverview,
            quizExerciseParticipation,
        }) => {
            await login(instructor, '/');
            await navigationBar.openCourseManagement();
            await courseManagement.openExercisesOfCourse(course.id!);
            const quizBatch = await quizExerciseOverview.addQuizBatch(quizExercise.id!);
            await quizExerciseOverview.startQuizBatch(quizExercise.id!, quizBatch.id!);
            await login(studentOne, `/courses/${course.id}/exercises/${quizExercise.id}`);
            await quizExerciseParticipation.joinQuizBatch(quizBatch.password!);
            await expect(quizExerciseParticipation.getQuizQuestion(0)).toBeVisible();
        });

        test('Instructor ends the quiz batch and student cannot participate anymore', async ({
            login,
            navigationBar,
            courseManagement,
            courseManagementExercises,
            courseOverview,
        }) => {
            await login(instructor, '/');
            await navigationBar.openCourseManagement();
            await courseManagement.openExercisesOfCourse(course.id!);
            await courseManagementExercises.endQuiz(quizExercise);
            await login(studentOne, `/courses/${course.id}/exercises/${quizExercise.id}`);
            await expect(courseOverview.getOpenRunningExerciseButton(quizExercise.id!)).not.toBeVisible();
        });

        test('Instructor ends exercise and student participates in practice mode', async ({
            login,
            navigationBar,
            courseManagement,
            courseManagementExercises,
            courseOverview,
            quizExerciseParticipation,
        }) => {
            await login(instructor, '/');
            await navigationBar.openCourseManagement();
            await courseManagement.openExercisesOfCourse(course.id!);
            await courseManagementExercises.endQuiz(quizExercise);
            await login(studentOne, `/courses/${course.id}/exercises/${quizExercise.id}`);
            await courseOverview.practiceExercise();
            await expect(quizExerciseParticipation.getQuizQuestion(0)).toBeVisible();
        });
    });

    test.describe('Quiz exercise individual participation', () => {
        let quizExercise: QuizExercise;

        test.beforeEach('Create quiz exercise', async ({ login, exerciseAPIRequests, courseManagementAPIRequests }) => {
            await login(admin);
            quizExercise = await exerciseAPIRequests.createQuizExercise({
                body: { course },
                quizQuestions: [multipleChoiceQuizTemplate],
                releaseDate: dayjs().subtract(1, 'weeks'),
                quizMode: QuizMode.INDIVIDUAL,
            });
            await courseManagementAPIRequests.addInstructorToCourse(course, instructor);
        });

        test('Student can start a batch in an individual quiz', async ({ login, courseOverview, quizExerciseParticipation }) => {
            await login(studentOne, `/courses/${course.id}/exercises/${quizExercise.id}`);
            await quizExerciseParticipation.startQuizBatch();
            await expect(quizExerciseParticipation.getQuizQuestion(0)).toBeVisible();
        });
    });

    test.describe('SA quiz participation', () => {
        let quizExercise: QuizExercise;

        test.beforeEach('Create SA quiz', async ({ login, exerciseAPIRequests }) => {
            await login(admin);
            quizExercise = await exerciseAPIRequests.createQuizExercise({ body: { course }, quizQuestions: [shortAnswerQuizTemplate] });
            await exerciseAPIRequests.setQuizVisible(quizExercise.id!);
            await exerciseAPIRequests.startQuizNow(quizExercise.id!);
        });

        test('Student can participate in SA quiz', async ({ login, courseOverview, quizExerciseShortAnswerQuiz }) => {
            await login(studentOne, `/courses/${course.id}/exercises/${quizExercise.id}`);
            const quizQuestionId = quizExercise.quizQuestions![0].id!;
            await quizExerciseShortAnswerQuiz.typeAnswer(0, 1, quizQuestionId, 'give');
            await quizExerciseShortAnswerQuiz.typeAnswer(1, 1, quizQuestionId, 'let');
            await quizExerciseShortAnswerQuiz.typeAnswer(2, 1, quizQuestionId, 'run');
            await quizExerciseShortAnswerQuiz.typeAnswer(2, 3, quizQuestionId, 'desert');
            await quizExerciseShortAnswerQuiz.typeAnswer(3, 1, quizQuestionId, 'cry');
            await quizExerciseShortAnswerQuiz.typeAnswer(4, 1, quizQuestionId, 'goodbye');
            await quizExerciseShortAnswerQuiz.submit();
        });
    });

    test.describe('DnD Quiz participation', () => {
        let quizExercise: QuizExercise;

        test.beforeEach('Create DND quiz', async ({ login, courseManagementExercises, exerciseAPIRequests, quizExerciseCreation }) => {
            await login(admin, '/course-management/' + course.id + '/exercises');
            await courseManagementExercises.createQuizExercise();
            await quizExerciseCreation.setTitle('Cypress Quiz');
            await quizExerciseCreation.addDragAndDropQuestion('DnD Quiz');
            const response = await quizExerciseCreation.saveQuiz();
            quizExercise = await response.json();
            await exerciseAPIRequests.setQuizVisible(quizExercise.id!);
            await exerciseAPIRequests.startQuizNow(quizExercise.id!);
        });

        test('Student can participate in DnD Quiz', async ({ login, courseOverview, quizExerciseDragAndDropQuiz }) => {
            await login(studentOne, `/courses/${course.id}/exercises/${quizExercise.id}`);
            await quizExerciseDragAndDropQuiz.dragItemIntoDragArea(0);
            await quizExerciseDragAndDropQuiz.submit();
        });
    });

    // Seed courses are persistent — no cleanup needed
});
