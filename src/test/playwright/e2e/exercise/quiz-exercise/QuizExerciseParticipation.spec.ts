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
            const tickedOptionIndices = [0, 2];
            for (const optionIndex of tickedOptionIndices) {
                await quizExerciseMultipleChoice.tickAnswerOption(quizExercise.id!, optionIndex);
            }
            const submitResponse = await quizExerciseMultipleChoice.submit();
            // Pin the submit contract end-to-end: the live endpoint must accept the DTO-shaped payload, mark the submission
            // as submitted, and return exactly the answer the student ticked (one MC entry with the right selected ids).
            expect(submitResponse.status()).toBe(200);
            const submittedExpectedIds = tickedOptionIndices.map((index) => quizExercise.quizQuestions![0].answerOptions![index].id);
            const responseBody = await submitResponse.json();
            expect(responseBody.submitted, 'server must flip the submitted flag after final submit').toBe(true);
            expect(responseBody.submittedAnswers, 'server must persist exactly one submitted answer for the MC question').toHaveLength(1);
            const mcAnswer = responseBody.submittedAnswers[0];
            expect(mcAnswer.type, 'persisted answer must keep the multiple-choice discriminator').toBe('multiple-choice');
            const persistedSelectedIds = (mcAnswer.selectedOptions ?? []).map((option: any) => option.id).sort((a: number, b: number) => a - b);
            expect(persistedSelectedIds, 'server must persist exactly the answer-option ids the student ticked').toEqual([...submittedExpectedIds].sort((a, b) => a - b));
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
                    .filter((q: any) => q.type === 'multiple-choice')
                    .flatMap((q: any) => (q.answerOptions ?? []).map((opt: any) => opt.id))
                    .sort((a: number, b: number) => a - b);
            };

            // Sanity check: IDs in the creation response match what the server now holds.
            expect(await readOptionIdsFromServer()).toEqual(initialOptionIds);

            await exerciseAPIRequests.setQuizVisible(createdQuiz.id!);
            expect(await readOptionIdsFromServer(), 'SET_VISIBLE must not regenerate AnswerOption ids').toEqual(initialOptionIds);

            const startNowResponse = await page.request.put(`/api/quiz/quiz-exercises/${createdQuiz.id}/start-now`);
            expect(startNowResponse.ok()).toBeTruthy();
            const startNowBody = await startNowResponse.json();
            // Guard against a regression where START_NOW silently skips persisting the batch startTime (e.g. an UPDATE
            // on a transient batch whose id is null would match no rows and the quiz would stay in "Waiting for Start"
            // for students, even though the child-id assertion below still passes).
            expect(startNowBody.startDate, 'START_NOW must return a persisted batch startTime').toBeTruthy();
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
            const typedAnswers = ['give', 'let', 'run', 'desert', 'cry', 'goodbye'];
            await quizExerciseShortAnswerQuiz.typeAnswer(0, 1, quizQuestionId, typedAnswers[0]);
            await quizExerciseShortAnswerQuiz.typeAnswer(1, 1, quizQuestionId, typedAnswers[1]);
            await quizExerciseShortAnswerQuiz.typeAnswer(2, 1, quizQuestionId, typedAnswers[2]);
            await quizExerciseShortAnswerQuiz.typeAnswer(2, 3, quizQuestionId, typedAnswers[3]);
            await quizExerciseShortAnswerQuiz.typeAnswer(3, 1, quizQuestionId, typedAnswers[4]);
            await quizExerciseShortAnswerQuiz.typeAnswer(4, 1, quizQuestionId, typedAnswers[5]);
            const submitResponse = await quizExerciseShortAnswerQuiz.submit();
            // End-to-end submit contract for short-answer: the new DTO-bound endpoint must accept the rich entity-shaped JSON the
            // client sends, persist one submitted-text per filled spot (lifting the text verbatim), and not silently drop any of them.
            expect(submitResponse.status()).toBe(200);
            const responseBody = await submitResponse.json();
            expect(responseBody.submitted).toBe(true);
            expect(responseBody.submittedAnswers, 'server must persist exactly one submitted answer for the SA question').toHaveLength(1);
            const saAnswer = responseBody.submittedAnswers[0];
            expect(saAnswer.type).toBe('short-answer');
            const persistedTexts = (saAnswer.submittedTexts ?? []).map((submittedText: any) => submittedText.text);
            expect(persistedTexts, 'server must persist a submitted-text entry for every spot the student filled').toHaveLength(typedAnswers.length);
            for (const expected of typedAnswers) {
                expect(persistedTexts, `server must preserve the typed text "${expected}" verbatim`).toContain(expected);
            }
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

        test('Student can participate in DnD Quiz', async ({ login, page, courseOverview, quizExerciseDragAndDropQuiz }) => {
            await login(studentOne, `/courses/${course.id}/exercises/${quizExercise.id}`);
            // Capture the response body before clicking submit (DragAndDropQuiz.submit() doesn't return it directly).
            const submitResponsePromise = page.waitForResponse(`api/quiz/exercises/*/submissions/live?submit=true`);
            await quizExerciseDragAndDropQuiz.dragItemIntoDragArea(0);
            await quizExerciseDragAndDropQuiz.submit();
            const submitResponse = await submitResponsePromise;
            // End-to-end submit contract for drag-and-drop: the DTO-bound endpoint must accept the entity-shaped JSON
            // (with full nested DragItem / DropLocation objects) the client sends and persist one mapping per drop the
            // student performed — server-resolved by id, not the client-supplied object.
            expect(submitResponse.status()).toBe(200);
            const responseBody = await submitResponse.json();
            expect(responseBody.submitted).toBe(true);
            expect(responseBody.submittedAnswers, 'server must persist exactly one submitted answer for the DnD question').toHaveLength(1);
            const dndAnswer = responseBody.submittedAnswers[0];
            expect(dndAnswer.type).toBe('drag-and-drop');
            expect(dndAnswer.mappings, 'server must persist a mapping for every drag the student performed').toHaveLength(1);
            const mapping = dndAnswer.mappings[0];
            expect(mapping.dragItem?.id, 'persisted mapping must reference a real dragItem id').toEqual(expect.any(Number));
            expect(mapping.dropLocation?.id, 'persisted mapping must reference a real dropLocation id').toEqual(expect.any(Number));
        });
    });

    // Seed courses are persistent — no cleanup needed
});
