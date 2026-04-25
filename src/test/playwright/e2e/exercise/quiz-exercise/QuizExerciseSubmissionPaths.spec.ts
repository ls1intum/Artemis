import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import multipleChoiceQuizTemplate from '../../../fixtures/exercise/quiz/multiple_choice/template.json';
import shortAnswerQuizTemplate from '../../../fixtures/exercise/quiz/short_answer/template.json';
import { admin, studentOne, tutor } from '../../../support/users';
import { test } from '../../../support/fixtures';
import { expect } from '@playwright/test';
import dayjs from 'dayjs';
import { QuizMode } from '../../../support/constants';
import { SEED_COURSES } from '../../../support/seedData';

const course = { id: SEED_COURSES.quizParticipation.id } as any;

/**
 * Per-mode coverage of the quiz submission contract from the student / tutor side.
 * <p>
 * The companion {@code QuizExerciseParticipation.spec.ts} already pins the live-mode UI flow with response-body assertions.
 * This file complements it by covering practice, training, and preview submission paths end-to-end (real HTTP through the
 * load balancer + Hazelcast cluster, real DB), each with strong assertions on the response shape and (where applicable) on
 * the persisted state. Each test creates its own quiz exercise so the suite is safe to run in any order and on a multi-node
 * stack where the same test may execute on any node behind the round-robin LB.
 */
test.describe('Quiz Exercise Submission Paths', { tag: '@fast' }, () => {
    test.describe('Practice mode (course quiz, student)', () => {
        let quizExercise: QuizExercise;

        test.beforeEach('Create an ended course quiz so it is open for practice', async ({ login, exerciseAPIRequests }) => {
            await login(admin);
            // Release in the past + due date in the past + duration of zero so the quiz is "ended" and the
            // practice button becomes available to the student. SYNCHRONIZED with a past-started batch is the
            // simplest configuration that mirrors what `quizExerciseUtilService.addCourseWithOneQuizExercise()`
            // does on the server side (the same path the practice integration tests use).
            quizExercise = await exerciseAPIRequests.createQuizExercise({
                body: { course },
                quizQuestions: [multipleChoiceQuizTemplate],
                releaseDate: dayjs().subtract(2, 'days'),
                dueDate: dayjs().subtract(1, 'days'),
                duration: 60,
                quizMode: QuizMode.SYNCHRONIZED,
            });
        });

        /**
         * Pins the practice contract end-to-end: a student picks the two correct answer options, submits via the
         * practice endpoint, and the server must (1) accept the request, (2) compute a 100% score, (3) persist a
         * submission with exactly the answer the student picked, and (4) wire the submission to a Result.
         */
        test('Student practice-submits a fully-correct MC answer and the server returns a 100% result', async ({ login, page }) => {
            await login(studentOne);

            const correctOptionIds = quizExercise
                .quizQuestions![0].answerOptions!.filter((option) => option.isCorrect)
                .map((option) => option.id!)
                .sort((a, b) => a - b);
            expect(correctOptionIds.length, 'fixture must define at least one correct option').toBeGreaterThan(0);

            // Use Playwright's request context for the submit so the test is independent of the
            // (much more complex) practice-mode UI flow — what we assert here is the server contract for
            // POST /submissions/practice that this PR's DTO migration changed.
            const practicePayload = {
                submittedAnswers: [
                    {
                        type: 'multiple-choice',
                        questionId: quizExercise.quizQuestions![0].id,
                        selectedOptions: correctOptionIds,
                    },
                ],
            };
            const submitResponse = await page.request.post(`/api/quiz/exercises/${quizExercise.id}/submissions/practice`, { data: practicePayload });
            expect(submitResponse.status(), 'practice submit must return 200 OK').toBe(200);

            const responseBody = await submitResponse.json();
            // The endpoint returns a ResultAfterEvaluationWithSubmissionDTO with the evaluated submission and result wrapper.
            expect(responseBody.score, 'practice result must reflect the per-question score: all correct → 100%').toBe(100);
            expect(responseBody.rated, 'practice results must be unrated').toBe(false);
            expect(responseBody.submission, 'response must embed the persisted submission').toBeDefined();
            expect(responseBody.submission.submitted, 'submission must be marked as submitted').toBe(true);
            const persistedAnswers = responseBody.submission.submittedAnswers;
            expect(persistedAnswers, 'practice must persist exactly one submitted answer per question').toHaveLength(1);
            const persistedMc = persistedAnswers[0];
            expect(persistedMc.scoreInPoints, 'persisted answer must carry the per-question score').toBe(quizExercise.quizQuestions![0].points);
            const persistedSelectedIds = (persistedMc.multipleChoiceSubmittedAnswer?.selectedOptions ?? persistedMc.selectedOptions ?? [])
                .map((option: any) => option.id)
                .sort((a: number, b: number) => a - b);
            expect(persistedSelectedIds, 'practice must persist exactly the option ids the student picked').toEqual(correctOptionIds);
        });

        /**
         * Negative path: a partially-correct MC submission must yield a partial (not zero, not 100%) score.
         * This guards against a regression where the DTO conversion silently drops the wrong selection or the
         * scoring path was wired to the wrong question.
         */
        test('Student practice-submits a partially-correct MC answer and receives a partial score', async ({ login, page }) => {
            await login(studentOne);

            const options = quizExercise.quizQuestions![0].answerOptions!;
            const oneCorrect = options.find((option) => option.isCorrect)!;
            const oneIncorrect = options.find((option) => !option.isCorrect)!;
            const submittedOptionIds = [oneCorrect.id!, oneIncorrect.id!];

            const practicePayload = {
                submittedAnswers: [
                    {
                        type: 'multiple-choice',
                        questionId: quizExercise.quizQuestions![0].id,
                        selectedOptions: submittedOptionIds,
                    },
                ],
            };
            const submitResponse = await page.request.post(`/api/quiz/exercises/${quizExercise.id}/submissions/practice`, { data: practicePayload });
            expect(submitResponse.status()).toBe(200);
            const responseBody = await submitResponse.json();
            // PROPORTIONAL_WITHOUT_PENALTY scoring on the fixture (2 correct, 2 incorrect): one correct + one incorrect = 50% of correct selected.
            expect(responseBody.score, 'partial answer must produce a partial score, not 0 and not 100').toBeGreaterThan(0);
            expect(responseBody.score, 'partial answer must produce a partial score, not 0 and not 100').toBeLessThan(100);
        });
    });

    test.describe('Training mode (course-wide spaced repetition, student)', () => {
        let quizExercise: QuizExercise;

        test.beforeEach('Create a course quiz whose questions can be trained', async ({ login, exerciseAPIRequests }) => {
            await login(admin);
            quizExercise = await exerciseAPIRequests.createQuizExercise({
                body: { course },
                quizQuestions: [multipleChoiceQuizTemplate],
                releaseDate: dayjs().subtract(2, 'days'),
                dueDate: dayjs().subtract(1, 'days'),
                duration: 60,
                quizMode: QuizMode.SYNCHRONIZED,
            });
        });

        /**
         * Pins the training-mode submission contract: a student answers a single course question correctly via the
         * training endpoint and the server must return an evaluated answer with the per-question score and the
         * full set of selected options preserved.
         */
        test('Student training-submits a correct MC answer and receives a full per-question score', async ({ login, page }) => {
            await login(studentOne);

            const question = quizExercise.quizQuestions![0];
            const correctOptionIds = question.answerOptions!.filter((option) => option.isCorrect).map((option) => option.id!);
            const trainingPayload = {
                type: 'multiple-choice',
                quizQuestion: { id: question.id },
                selectedOptions: correctOptionIds.map((id) => ({ id })),
            };
            const submitResponse = await page.request.post(`/api/quiz/courses/${course.id}/training-questions/${question.id}/submit?isRated=true`, {
                data: trainingPayload,
            });
            expect(submitResponse.status(), 'training submit must return 200 OK for a question that belongs to the path course').toBe(200);

            const responseBody = await submitResponse.json();
            expect(responseBody.scoreInPoints, 'all-correct training answer must score full per-question points').toBe(question.points);
            expect(responseBody.multipleChoiceSubmittedAnswer, 'response must contain the multiple-choice answer payload').toBeDefined();
            const persistedSelectedIds = (responseBody.multipleChoiceSubmittedAnswer.selectedOptions ?? []).map((option: any) => option.id).sort((a: number, b: number) => a - b);
            expect(persistedSelectedIds, 'training response must echo back the selected option ids the student submitted').toEqual([...correctOptionIds].sort((a, b) => a - b));
        });

        /**
         * Security regression for the course-scoped question lookup: passing a {@code quizQuestionId} from one course
         * while addressing a different course's id in the path must 404, not return the question content (which would
         * leak {@code QuizQuestionWithSolutionDTO} to a student outside the course).
         */
        test('Training submit with a question id from a different course returns 404 (no solution leak)', async ({ login, page, exerciseAPIRequests }) => {
            // Create a second quiz on the OTHER seed course (quizAssessment) so we have a question id that genuinely
            // does not belong to the course we will pass in the training path below.
            await login(admin);
            const courseOther = { id: SEED_COURSES.quizAssessment.id } as any;
            const quizInOtherCourse = await exerciseAPIRequests.createQuizExercise({
                body: { course: courseOther },
                quizQuestions: [multipleChoiceQuizTemplate],
                releaseDate: dayjs().subtract(2, 'days'),
                dueDate: dayjs().subtract(1, 'days'),
                duration: 60,
                quizMode: QuizMode.SYNCHRONIZED,
            });
            const foreignQuestionId = quizInOtherCourse.quizQuestions![0].id!;

            await login(studentOne);

            const trainingPayload = {
                type: 'multiple-choice',
                quizQuestion: { id: foreignQuestionId },
                selectedOptions: quizInOtherCourse.quizQuestions![0].answerOptions!.filter((option) => option.isCorrect).map((option) => ({ id: option.id })),
            };
            // Attempt to submit the foreign question using THIS describe's `course.id` in the path → must 404.
            const submitResponse = await page.request.post(`/api/quiz/courses/${course.id}/training-questions/${foreignQuestionId}/submit?isRated=true`, {
                data: trainingPayload,
            });
            expect(submitResponse.status(), 'cross-course training submit must be rejected with 404, never leak the question').toBe(404);
        });

        /**
         * Strict-type guard: a multiple-choice payload submitted for a non-MC question id must be rejected with 400.
         * The training endpoint deliberately uses strict conversion (unlike live mode's lenient conversion), so a
         * mismatched answer must surface as a client error rather than silently scoring zero.
         */
        test('Training submit with a payload type that does not match the question type returns 400', async ({ login, page, exerciseAPIRequests }) => {
            await login(admin);
            // Create a SA quiz to get a non-MC question id; then submit a MC payload against that id.
            const shortAnswerQuiz = await exerciseAPIRequests.createQuizExercise({
                body: { course },
                quizQuestions: [shortAnswerQuizTemplate],
                releaseDate: dayjs().subtract(2, 'days'),
                dueDate: dayjs().subtract(1, 'days'),
                duration: 60,
                quizMode: QuizMode.SYNCHRONIZED,
            });
            const saQuestionId = shortAnswerQuiz.quizQuestions![0].id!;

            await login(studentOne);

            const mcPayloadForSaQuestion = {
                type: 'multiple-choice',
                quizQuestion: { id: saQuestionId },
                selectedOptions: [],
            };
            const submitResponse = await page.request.post(`/api/quiz/courses/${course.id}/training-questions/${saQuestionId}/submit?isRated=true`, {
                data: mcPayloadForSaQuestion,
            });
            expect(submitResponse.status(), 'training endpoint must reject a mismatched answer payload type with 400, not 5xx').toBe(400);
        });
    });

    test.describe('Preview mode (tutor, ephemeral)', () => {
        let quizExercise: QuizExercise;

        test.beforeEach('Create a course quiz the tutor can preview', async ({ login, exerciseAPIRequests }) => {
            await login(admin);
            // The seed course already wires the test tutor (artemis_test_user_6) into its `artemis-e2equizpart-tutors`
            // group via the e2e seed data, so no extra enrollment call is needed for @EnforceAtLeastTutorInExercise.
            quizExercise = await exerciseAPIRequests.createQuizExercise({ body: { course }, quizQuestions: [multipleChoiceQuizTemplate], duration: 60 });
        });

        /**
         * Preview-mode contract: a tutor previews the quiz with a fully-correct answer and the server returns the
         * evaluated result without persisting a submission. The previous DTO migration kept this path on the existing
         * {@code QuizSubmissionFromStudentDTO}, so this test pins that the contract still works end-to-end after the
         * shared question-conversion code was refactored.
         */
        test('Tutor preview-submits a fully-correct MC answer, receives a 100% result, and nothing is persisted', async ({ login, page }) => {
            await login(tutor);

            const correctOptionIds = quizExercise.quizQuestions![0].answerOptions!.filter((option) => option.isCorrect).map((option) => option.id!);

            const previewPayload = {
                submittedAnswers: [
                    {
                        type: 'multiple-choice',
                        questionId: quizExercise.quizQuestions![0].id,
                        selectedOptions: correctOptionIds,
                    },
                ],
            };
            const previewResponse = await page.request.post(`/api/quiz/exercises/${quizExercise.id}/submissions/preview`, { data: previewPayload });
            expect(previewResponse.status(), 'preview submit must return 200 OK for an authorized tutor').toBe(200);

            const previewBody = await previewResponse.json();
            expect(previewBody.score, 'preview result must score the answer just like live evaluation: all correct → 100').toBe(100);
            expect(previewBody.rated, 'preview results are intentionally unrated').toBe(false);
            expect(previewBody.submission, 'preview response must include an evaluated (transient) submission').toBeDefined();
            expect(previewBody.submission.submitted, 'preview submission must carry the submitted=true flag for downstream display').toBe(true);
        });
    });

    test.describe('Exam mode (course quiz inside an exam exercise group)', () => {
        let quizExercise: QuizExercise;

        test.beforeEach('Create a course quiz that the existing live-mode endpoint can save against', async ({ login, exerciseAPIRequests }) => {
            await login(admin);
            quizExercise = await exerciseAPIRequests.createQuizExercise({
                body: { course },
                quizQuestions: [multipleChoiceQuizTemplate],
                releaseDate: dayjs().subtract(1, 'minutes'),
                duration: 600,
                quizMode: QuizMode.SYNCHRONIZED,
            });
            await exerciseAPIRequests.setQuizVisible(quizExercise.id!);
            await exerciseAPIRequests.startQuizNow(quizExercise.id!);
        });

        /**
         * The {@code submissions/exam} endpoint is the PUT counterpart of {@code submissions/live} for quiz exercises
         * inside an exam. For a course-mode quiz exercise (the fixture used by these E2E tests), the resource code
         * path falls through {@code if (quizExercise.isExamExercise())} and the endpoint behaves like a save-only
         * upsert that persists exactly what the student sent. This test pins that the DTO-bound endpoint still accepts
         * the rich entity-shaped JSON the exam client posts and persists the answer with the right discriminator.
         */
        test('Student exam-mode PUT persists a multiple-choice submission with the right discriminator', async ({ login, page }) => {
            await login(studentOne);
            await page.request.post(`/api/quiz/quiz-exercises/${quizExercise.id}/start-participation`);

            const options = quizExercise.quizQuestions![0].answerOptions!;
            const tickedOptionIds = options.filter((option) => option.isCorrect).map((option) => option.id!);
            // Mirror the rich entity-shaped JSON the exam client serializes (full nested AnswerOption objects).
            const examPayload = {
                submissionExerciseType: 'quiz',
                submitted: true,
                submittedAnswers: [
                    {
                        type: 'multiple-choice',
                        quizQuestion: quizExercise.quizQuestions![0],
                        selectedOptions: options.filter((option) => tickedOptionIds.includes(option.id!)),
                    },
                ],
            };
            const submitResponse = await page.request.put(`/api/quiz/exercises/${quizExercise.id}/submissions/exam`, { data: examPayload });
            expect(submitResponse.status(), 'exam-mode PUT must return 200 even for non-exam quiz exercises (the resource gracefully skips the exam-API guard)').toBe(200);

            const responseBody = await submitResponse.json();
            expect(responseBody.submitted, 'server must keep the submitted flag on the persisted submission').toBe(true);
            expect(responseBody.submittedAnswers, 'exam PUT must persist exactly one submitted answer for the single question').toHaveLength(1);
            const persistedAnswer = responseBody.submittedAnswers[0];
            expect(persistedAnswer.type, 'persisted answer must keep the multiple-choice discriminator').toBe('multiple-choice');
            const persistedSelectedIds = (persistedAnswer.selectedOptions ?? []).map((option: any) => option.id).sort((a: number, b: number) => a - b);
            expect(persistedSelectedIds, 'exam PUT must persist exactly the option ids the student ticked').toEqual([...tickedOptionIds].sort((a, b) => a - b));
        });
    });

    // Seed courses are persistent; per-test quiz exercises are scoped to each test and cleaned up by the seed reset between runs.
});
