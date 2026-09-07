import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { MultipleChoiceQuestion } from 'app/quiz/shared/entities/multiple-choice-question.model';
import multipleChoiceQuizTemplate from '../../../fixtures/exercise/quiz/multiple_choice/template.json';
import shortAnswerQuizTemplate from '../../../fixtures/exercise/quiz/short_answer/template.json';
import { admin, instructor, studentOne } from '../../../support/users';
import { test } from '../../../support/fixtures';
import { expect } from '@playwright/test';
import dayjs from 'dayjs';
import { QuizMode } from '../../../support/constants';
import { SEED_COURSES } from '../../../support/seedData';
import { generateUUID, readResponseJson } from '../../../support/utils';

const course = { id: SEED_COURSES.quizParticipation.id } as any;

/**
 * The answer options of a multiple choice question. `QuizExercise.quizQuestions` is typed as the abstract question,
 * so the options only become visible once the concrete type is named - which the multiple choice fixtures always are.
 */
function answerOptionsOf(quiz: QuizExercise, questionIndex = 0) {
    return (quiz.quizQuestions![questionIndex] as MultipleChoiceQuestion).answerOptions!;
}

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
            const submittedExpectedIds = tickedOptionIndices.map((index) => answerOptionsOf(quizExercise)[index].id!);
            const responseBody = await readResponseJson(submitResponse);
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
            // The scheduled quiz-evaluation job + repeated page reloads make this test
            // routinely exceed the 60s fast-test budget under parallel CI load. The 120s
            // evaluation poll alone consumes most of `test.slow()`'s 180s budget under
            // heavy multi-node load, so set an explicit 6-minute timeout that comfortably
            // covers worst-case scheduler delay (120s) + 3 verification reloads (≤30s each).
            test.setTimeout(360_000);
            // Quiz duration must comfortably cover the student-side login → tick options →
            // submit chain. With duration = 10s the chain often does not fit under multi-node
            // CI load (login alone can take 10-15s), the quiz auto-ends before submit, and
            // `#submit-exercise` becomes permanently disabled (the failure mode observed in
            // 26335092464). 60s leaves a wide margin while keeping evaluation latency
            // bounded — the post-end evaluation poll budget is 120s, so end-to-end this test
            // still completes well within its 360s setTimeout.
            const quizDurationSeconds = 60;
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
            const expectedTickedOptionIds = tickedOptionIndices.map((index) => answerOptionsOf(shortQuiz)[index].id!);
            expect(expectedTickedOptionIds).toHaveLength(tickedOptionIndices.length);

            /**
             * Reload the participation page and read the server's response to `/start-participation`. Returns the set of selected option ids for
             * the MC question on this response, or null when evaluation has not yet populated a rated result (e.g. the `results` array is still empty).
             */
            async function reloadAndReadSelectedOptionIds(): Promise<number[] | null> {
                // Bound the response wait to 45s so a single hung request (the multi-node
                // observation under load) does not consume the entire test budget. On
                // timeout we re-issue the navigation up to two more times before giving up
                // — a hung start-participation POST is a backend race that consistently
                // recovers on subsequent retries within 1-2 attempts.
                for (let attempt = 0; attempt < 3; attempt++) {
                    const responsePromise = page.waitForResponse(
                        (response) =>
                            response.url().includes(`/api/quiz/quiz-exercises/${shortQuiz.id}/start-participation`) && response.request().method() === 'POST' && response.ok(),
                        { timeout: 45_000 },
                    );
                    await page.goto(`/courses/${course.id}/exercises/${shortQuiz.id!}`);
                    let body: any;
                    try {
                        body = await readResponseJson(await responsePromise);
                    } catch {
                        if (attempt === 2) {
                            throw new Error(`reloadAndReadSelectedOptionIds: start-participation never returned after 3 attempts`);
                        }
                        continue;
                    }
                    const submission = (body.submissions ?? [])[0];
                    if (!submission || !(submission.results ?? []).length) {
                        return null;
                    }
                    const mcAnswer = (submission.submittedAnswers ?? []).find((submittedAnswer: any) => submittedAnswer.quizQuestion?.id === mcQuestionId);
                    return mcAnswer ? (mcAnswer.selectedOptions ?? []).map((option: any) => option.id).sort((a: number, b: number) => a - b) : [];
                }
                return null;
            }

            // Poll the refresh endpoint until the scheduled evaluation job has created a rated
            // result — more robust than a fixed sleep on loaded CI workers. The scheduled job
            // fires after the quiz `duration` window closes, so under heavy parallel CI load
            // the job can be delayed by 60-90s waiting for an idle scheduler thread.
            const evaluationTimeoutMs = 120_000;
            await expect
                .poll(() => reloadAndReadSelectedOptionIds().then((ids) => ids !== null), {
                    message: 'evaluation did not produce a rated result within the expected window',
                    timeout: evaluationTimeoutMs,
                    intervals: [1000, 2000, 3000, 5000],
                })
                .toBe(true);

            const expectedSortedOptionIds = [...expectedTickedOptionIds].sort((a, b) => a - b);
            // Reload several times after evaluation completes; the bug manifested non-deterministically,
            // so the loop amplifies any remaining flakiness. Three reloads is the sweet spot: enough to
            // catch the original eagerly-fetched / cached-collection regression, few enough to fit the
            // slow-test budget under heavy parallel CI load.
            for (let iteration = 0; iteration < 3; iteration++) {
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
            const initialOptionIds = answerOptionsOf(createdQuiz)
                .map((option) => option.id!)
                .sort((a: number, b: number) => a - b);
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
        // 15s was too tight: beforeEach API call + login + navigation can consume 10–15s,
        // leaving zero margin before startOfWorkingTime arrives and the overlay disappears.
        // 45s gives ~30s of headroom for the "cannot participate" assertion.
        const timeUntilQuizStartInSeconds = 45;

        test.beforeEach('Create quiz exercise', async ({ login, exerciseAPIRequests }) => {
            await login(admin);
            const releaseDate = dayjs();
            const startOfWorkingTime = releaseDate.add(timeUntilQuizStartInSeconds, 'seconds');
            quizExercise = await exerciseAPIRequests.createQuizExercise({ body: { course }, quizQuestions: [multipleChoiceQuizTemplate], releaseDate, startOfWorkingTime });
        });

        test('Student cannot participate in scheduled quiz before start of working time', async ({ page, login, courseOverview, quizExerciseParticipation }) => {
            // Wait for the page's initial GET /courses/.../for-dashboard to settle before
            // looking for the overlay — the overlay is gated on that fetch returning the
            // quiz's startOfWorkingTime. Without the explicit wait the default 10s expect
            // timeout can fire under multi-node CI load while the request is still in flight,
            // even though the overlay would render seconds later.
            const dashboardResponse = page
                .waitForResponse((resp) => resp.url().includes(`api/course/courses/${course.id}/for-dashboard`) && resp.ok(), { timeout: 30_000 })
                .catch(() => undefined);
            await login(studentOne, `/courses/${course.id}/exercises/${quizExercise.id}`);
            await dashboardResponse;
            await expect(quizExerciseParticipation.getWaitingForStartAlert()).toBeVisible();
        });

        test('Student can participate in scheduled quiz when working time arrives', async ({ page, login, courseOverview, quizExerciseParticipation }) => {
            // timeUntilQuizStartInSeconds is 45s — lift the per-test budget so the fixed
            // wait below doesn't hit the 60s @fast default.
            test.slow();
            await login(studentOne, `/courses/${course.id}/exercises/${quizExercise.id}`);
            // The quiz page does not push a live update when startOfWorkingTime arrives;
            // wait for the time to pass, then assert the overlay is gone and the question shows.
            await page.waitForTimeout(timeUntilQuizStartInSeconds * 1000 + 3000);
            await expect(quizExerciseParticipation.getWaitingForStartAlert()).not.toBeVisible({ timeout: 10000 });
            await expect(quizExerciseParticipation.getQuizQuestion(0)).toBeVisible({ timeout: 10000 });
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
            // Started through the dedicated action button, as the other practice tests in this file do. The generic
            // "click a button containing Practice" helper this used to call does not start a practice attempt: the page
            // offers several controls whose label contains the word, and the failure snapshot showed the exercise page
            // still displaying an untouched "Start practice" button while the test waited for a question to appear.
            await courseOverview.startQuizPractice(quizExercise.id!);
            await expect(quizExerciseParticipation.getQuizQuestion(0)).toBeVisible();
        });
    });

    test.describe('Quiz exercise practice mode', () => {
        let practiceQuiz: QuizExercise;

        test.beforeEach('Create an ended course quiz that is open for practice', async ({ login, exerciseAPIRequests }) => {
            await login(admin);
            // A quiz whose due date is in the past has ended and is therefore open for practice.
            practiceQuiz = await exerciseAPIRequests.createQuizExercise({
                body: { course },
                quizQuestions: [multipleChoiceQuizTemplate],
                releaseDate: dayjs().subtract(2, 'days'),
                dueDate: dayjs().subtract(1, 'days'),
                duration: 60,
                quizMode: QuizMode.SYNCHRONIZED,
            });
        });

        /**
         * Regression test for https://github.com/ls1intum/Artemis/issues/12955 (PR #12972). Two practice-mode bugs:
         *  - Bug 1: after submitting a new practice attempt, every prior attempt must stay in the result-history
         *    dropdown WITHOUT a page refresh (the participation merge must not drop earlier submissions).
         *  - Bug 2: viewing a practice attempt must render per-question correctness. Practice results are unrated, and
         *    the result endpoint used to filter unrated results out, so showResult never fired and no correctness showed.
         */
        test('keeps all practice attempts in the result history without refresh and shows per-question correctness', async ({
            login,
            courseOverview,
            quizExerciseMultipleChoice,
            quizExerciseParticipation,
            page,
        }) => {
            test.setTimeout(180_000);
            await login(studentOne, `/courses/${course.id}/exercises/${practiceQuiz.id}`);

            // --- First practice attempt ---
            await courseOverview.startQuizPractice(practiceQuiz.id!);
            await expect(quizExerciseParticipation.getQuizQuestion(0)).toBeVisible();
            await quizExerciseMultipleChoice.tickAnswerOption(practiceQuiz.id!, 0);
            const firstSubmit = await quizExerciseParticipation.submitPractice();
            expect(firstSubmit.status(), 'practice submit must return 200 OK').toBe(200);
            // The just-submitted attempt renders its per-question correctness table immediately.
            await expect(quizExerciseParticipation.getMultipleChoiceResultTable()).toBeVisible();

            // --- Second practice attempt, in the same session (no page reload) ---
            await courseOverview.startQuizPractice(practiceQuiz.id!);
            await expect(quizExerciseParticipation.getQuizQuestion(0)).toBeVisible();
            await quizExerciseMultipleChoice.tickAnswerOption(practiceQuiz.id!, 1);
            const secondSubmit = await quizExerciseParticipation.submitPractice();
            expect(secondSubmit.status(), 'second practice submit must return 200 OK').toBe(200);

            // Bug 1: the result-history dropdown must list BOTH attempts without a refresh.
            await quizExerciseParticipation.openResultHistory();
            await expect(quizExerciseParticipation.getResultHistoryRows()).toHaveCount(2);

            // Bug 2: opening an earlier attempt loads its (unrated) result via getParticipationResult and renders the
            // per-question correctness table. The newest attempt is listed first, so the last row is the oldest one.
            const resultResponse = page.waitForResponse(
                (response) => /\/api\/quiz\/quiz-exercises\/\d+\/participations\/\d+\/result/.test(response.url()) && response.status() === 200,
            );
            await quizExerciseParticipation.getResultHistoryRows().last().click();
            await resultResponse;
            await expect(quizExerciseParticipation.getMultipleChoiceResultTable()).toBeVisible();
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
            const responseBody = await readResponseJson(submitResponse);
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
            // Unique per test: three tests in this block each create a quiz and nothing deletes them, so a
            // fixed title would make the by-title recovery lookup below ambiguous.
            const quizTitle = 'Cypress Quiz ' + generateUUID();
            await quizExerciseCreation.setTitle(quizTitle);
            await quizExerciseCreation.addDragAndDropQuestion('DnD Quiz');
            const response = await quizExerciseCreation.saveQuiz();
            // The drag-and-drop background image is a disk-backed file, so this create response cannot be held
            // in Node and Chrome discards it when the editor navigates away on save. Fall back to an idempotent
            // lookup instead of failing the whole describe block on a body that no longer exists.
            quizExercise = await readResponseJson<QuizExercise>(response, () => exerciseAPIRequests.getQuizExerciseByTitle(course.id!, quizTitle));
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
            const responseBody = await readResponseJson(submitResponse);
            expect(responseBody.submitted).toBe(true);
            expect(responseBody.submittedAnswers, 'server must persist exactly one submitted answer for the DnD question').toHaveLength(1);
            const dndAnswer = responseBody.submittedAnswers[0];
            expect(dndAnswer.type).toBe('drag-and-drop');
            expect(dndAnswer.mappings, 'server must persist a mapping for every drag the student performed').toHaveLength(1);
            const mapping = dndAnswer.mappings[0];
            expect(mapping.dragItem?.id, 'persisted mapping must reference a real dragItem id').toEqual(expect.any(Number));
            expect(mapping.dropLocation?.id, 'persisted mapping must reference a real dropLocation id').toEqual(expect.any(Number));
        });

        /**
         * Regression test for https://github.com/ls1intum/Artemis/issues/13187: on small screens the drag items are
         * rendered below the exercise, so students must be able to scroll upwards while dragging an item. Holding a
         * dragged item near the top edge of the scroll container must auto-scroll it (CDK auto-scroll requires the
         * container to be registered as cdkScrollable).
         */
        test('View auto-scrolls when student drags an item to the top of the scroll container', async ({ login, page }) => {
            await login(studentOne, `/courses/${course.id}/exercises/${quizExercise.id}`);
            await page.setViewportSize({ width: 800, height: 600 });
            const dragItem = page.locator('#drag-item-0');
            await dragItem.waitFor({ state: 'visible' });

            // Wait until the quiz overflows a container that the CDK will actually auto-scroll, then mark that container.
            //
            // Two reasons for the wait. The drag items render as soon as the question arrives, but the question's
            // background image is fetched as a separate blob request and contributes its height only once decoded, so the
            // overflow appears late. Waiting for the overflow rather than for the image keeps this independent of how the
            // image is delivered and covers a question that has none.
            //
            // The container must also carry cdkScrollable, because that registration is what #13190 added and what this
            // test exists to protect: the CDK only auto-scrolls containers it knows about. Requiring it here means a
            // regression that leaves the scrolling element unregistered fails on this wait, naming the cause, instead of
            // timing out later while watching an element that was never going to move.
            await page.waitForFunction(
                () => {
                    let element = document.getElementById('drag-item-0')?.parentElement;
                    while (element) {
                        const style = getComputedStyle(element);
                        const scrollable = element.scrollHeight > element.clientHeight && ['auto', 'scroll'].includes(style.overflowY);
                        if (scrollable && element.hasAttribute('cdkscrollable')) {
                            element.setAttribute('data-e2e-scroll-container', 'true');
                            return true;
                        }
                        element = element.parentElement;
                    }
                    return false;
                },
                // The second parameter is the argument handed to the browser callback, so the options belong third.
                undefined,
                { timeout: 30_000 },
            );

            // Scroll the container to the bottom explicitly instead of relying on scrollIntoViewIfNeeded.
            //
            // This is what CI was failing on: the container was scrollable, but the drag item happened to be visible
            // already, so scrollIntoViewIfNeeded had nothing to do and the offset stayed at 0. Whether it has anything to
            // do depends on how tall the rest of the question renders, which depends on the background image, so the test
            // was asserting a precondition it had only incidentally arranged. Setting the offset makes the starting state
            // the test's own decision.
            const before = await page.evaluate(() => {
                const element = document.querySelector('[data-e2e-scroll-container]')!;
                element.scrollTop = element.scrollHeight;
                const rect = element.getBoundingClientRect();
                return { scrollTop: element.scrollTop, top: rect.top, left: rect.left, width: rect.width };
            });
            expect(before.scrollTop, 'precondition: the container must be scrolled down so it can scroll up during the drag').toBeGreaterThan(0);

            const box = await dragItem.boundingBox();
            if (!box) {
                throw new Error('the drag item must be visible and have a bounding box');
            }
            await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
            await page.mouse.down();
            // Drag the item to the top edge of the scroll container and hold it there, jiggling the pointer
            // so drag-move events keep firing until the auto-scroll has moved the container upwards.
            const holdX = before.left + before.width / 2;
            const holdY = before.top + 5;
            await page.mouse.move(holdX, holdY, { steps: 10 });
            // Counted here rather than taken from the callback: expect.poll invokes its callback without arguments, so a
            // parameter would stay at its default and the pointer would never actually move.
            let jiggle = 0;
            try {
                // Waited for as a condition rather than a fixed number of iterations: the CDK moves the container on
                // animation frames, which a busy machine delivers late, and a fixed ceiling turns that delay into a
                // failure of behaviour that is in fact correct.
                await expect
                    .poll(
                        async () => {
                            // Alternate the pointer position so every poll dispatches a move the drag can react to.
                            await page.mouse.move(holdX + (jiggle++ % 2), holdY);
                            return page.evaluate(() => document.querySelector('[data-e2e-scroll-container]')!.scrollTop);
                        },
                        {
                            message: 'holding a dragged item at the top edge must scroll the container upwards',
                            timeout: 15_000,
                            intervals: [100],
                        },
                    )
                    .toBeLessThan(before.scrollTop);
            } finally {
                // Always release the pointer, so a failure here cannot leave a held drag behind for the next assertion.
                await page.mouse.up();
            }
        });
    });

    // Seed courses are persistent — no cleanup needed
});
