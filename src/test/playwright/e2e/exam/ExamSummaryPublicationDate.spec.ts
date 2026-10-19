import { ArtemisCommands, ArtemisRequests, test } from '../../support/fixtures';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { expect } from '@playwright/test';
import { admin, studentTwo } from '../../support/users';
import { generateUUID } from '../../support/utils';
import dayjs from 'dayjs';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { SEED_COURSES } from '../../support/seedData';
import textExerciseTemplate from '../../fixtures/exercise/text/template.json';

/**
 * Verifies the optional exam.examSummaryPublicationDate: when it is set to the future, the student submission overview
 * ("Zusammenfassung", incl. exam questions, the student's own answers and the PDF export) is withheld after submission.
 * The student only sees a submission-received confirmation with the release date, and the /summary request is rejected.
 *
 * This guards the staggered/multi-shift exam use case where early submitters must not be able to re-download and leak the
 * exam content to later shifts before every shift has finished.
 *
 * Both states are covered: gated (a publication date in the future) and the default (no publication date at all, which is
 * what nearly every exam uses). The default case matters because the gate fails open on a missing value, so the server
 * omitting the field is indistinguishable from "not configured". That is exactly how the field went missing from
 * ExamForConductionDTO once the exam response endpoints were ported to DTOs.
 *
 * Two further branches of the gate are deliberately NOT covered here, because the API's own validation
 * (ExamResource#checkExamForDatesConflictsElseThrow) makes them unreachable through it:
 * - A publication date already in the past requires an exam that has ended (the date must be after the end date), and a
 *   student cannot sit an exam that is over. Reaching it needs a wall-clock wait for the date to pass mid-test, which is
 *   the shape of the flakiest specs in this suite, so it is covered by unit tests instead.
 * - The publishResultsDate safeguard needs a future publication date together with already-published results, but the
 *   summary date may never be after the results date. It is defensive only, so it is covered by unit tests on both
 *   Exam#isExamSummaryPublished and its client counterpart in exam.utils.ts.
 */

const course = { id: SEED_COURSES.examParticipation.id } as any;

test.describe('Exam submission overview publication date', { tag: '@slow' }, () => {
    let exam: Exam;
    let exercise: TextExercise;
    let groupTitle: string;
    let studentExamId: number;

    /**
     * Creates an active exam with a single text exercise, registers studentTwo and prepares the exercise start.
     *
     * @param summaryPublicationDate when the submission overview becomes visible, or undefined for the default
     *                               (immediately available after submission)
     */
    async function createExamWithOneTextExercise(
        login: ArtemisCommands['login'],
        examAPIRequests: ArtemisRequests['examAPIRequests'],
        exerciseAPIRequests: ArtemisRequests['exerciseAPIRequests'],
        summaryPublicationDate?: dayjs.Dayjs,
    ) {
        await login(admin);

        exam = await examAPIRequests.createExam({
            course,
            // short single-word title: the exam channel name is derived as titleLowercase(title) and must match ^[a-z0-9$][a-z0-9:-]{0,30}$ (max 31 chars, no spaces)
            title: 'exam' + generateUUID(),
            visibleDate: dayjs().subtract(3, 'minutes'),
            startDate: dayjs().subtract(2, 'minutes'),
            endDate: dayjs().add(1, 'hour'),
            examSummaryPublicationDate: summaryPublicationDate,
            examMaxPoints: 10,
            numberOfExercisesInExam: 1,
        });

        groupTitle = 'Group ' + generateUUID();
        const exerciseGroup = await examAPIRequests.addExerciseGroupForExam(exam, groupTitle);
        exercise = await exerciseAPIRequests.createTextExercise({ exerciseGroup }, 'Exercise ' + generateUUID(), textExerciseTemplate);

        await examAPIRequests.registerStudentForExam(exam, studentTwo);
        await examAPIRequests.generateMissingIndividualExams(exam);
        const studentExams = await examAPIRequests.getAllStudentExams(exam);
        studentExamId = studentExams[0].id!;
        await examAPIRequests.prepareExerciseStartForExam(exam);
    }

    test('withholds the submission overview until the publication date', async ({
        page,
        login,
        examAPIRequests,
        exerciseAPIRequests,
        examParticipation,
        examNavigation,
        textExerciseEditor,
    }) => {
        // the submission overview must only become visible far in the future (after the last shift)
        await createExamWithOneTextExercise(login, examAPIRequests, exerciseAPIRequests, dayjs().add(1, 'day'));

        await examParticipation.startParticipation(studentTwo, course, exam);
        await examNavigation.openOrSaveExerciseByTitle(groupTitle);
        await textExerciseEditor.typeSubmission(exercise.id!, 'my answer to the exercise.');

        await examParticipation.handInEarly();

        // The submission was received, but the overview and PDF export are withheld until the publication date.
        await expect(page.locator('[data-testid="examSummaryUnavailableHint"]')).toBeVisible({ timeout: 20000 });
        await expect(page.locator('#showExamSummaryButton')).toBeHidden();
        await expect(page.locator('#exportToPDFButton')).toBeHidden();

        // The server rejects the summary request while the publication date is still in the future.
        const summaryResponse = await page.request.get(`api/exam/courses/${course.id}/exams/${exam.id}/student-exams/${studentExamId}/summary`);
        expect(summaryResponse.status()).toBe(403);
    });

    test('offers the submission overview right away when no publication date is set', async ({
        page,
        login,
        examAPIRequests,
        exerciseAPIRequests,
        examParticipation,
        examNavigation,
        textExerciseEditor,
    }) => {
        await createExamWithOneTextExercise(login, examAPIRequests, exerciseAPIRequests, undefined);

        await examParticipation.startParticipation(studentTwo, course, exam);
        await examNavigation.openOrSaveExerciseByTitle(groupTitle);
        await textExerciseEditor.typeSubmission(exercise.id!, 'my answer to the exercise.');

        await examParticipation.handInEarly();

        // Default behaviour: the overview is offered as soon as the submission is in, and no release hint is shown.
        await expect(page.locator('#showExamSummaryButton')).toBeVisible({ timeout: 20000 });
        await expect(page.locator('[data-testid="examSummaryUnavailableHint"]')).toBeHidden();

        // The server serves the summary, so the gate is open on both sides and not just visually.
        const summaryResponse = await page.request.get(`api/exam/courses/${course.id}/exams/${exam.id}/student-exams/${studentExamId}/summary`);
        expect(summaryResponse.status()).toBe(200);
    });

    test.afterEach('Delete exam', async ({ login, examAPIRequests }) => {
        await login(admin);
        await examAPIRequests.deleteExam(exam);
    });
});
