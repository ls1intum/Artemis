import { test } from '../../support/fixtures';
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
 */

const course = { id: SEED_COURSES.examParticipation.id } as any;

test.describe('Exam submission overview publication date', { tag: '@slow' }, () => {
    let exam: Exam;
    let exercise: TextExercise;
    let groupTitle: string;
    let studentExamId: number;

    test.beforeEach('Create exam with a future summary publication date', async ({ login, examAPIRequests, exerciseAPIRequests }) => {
        await login(admin);

        exam = await examAPIRequests.createExam({
            course,
            // short single-word title: the exam channel name is derived as titleLowercase(title) and must match ^[a-z0-9$][a-z0-9:-]{0,30}$ (max 31 chars, no spaces)
            title: 'exam' + generateUUID(),
            visibleDate: dayjs().subtract(3, 'minutes'),
            startDate: dayjs().subtract(2, 'minutes'),
            endDate: dayjs().add(1, 'hour'),
            // the submission overview must only become visible far in the future (after the last shift)
            examSummaryPublicationDate: dayjs().add(1, 'day'),
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
    });

    test('withholds the submission overview until the publication date', async ({ page, examParticipation, examNavigation, examStartEnd, textExerciseEditor }) => {
        await examParticipation.startParticipation(studentTwo, course, exam);
        await examNavigation.openOrSaveExerciseByTitle(groupTitle);
        await textExerciseEditor.typeSubmission(exercise.id!, 'my answer to the exercise.');

        await examParticipation.handInEarly();

        // The submission was received, but the overview and PDF export are withheld until the publication date.
        await expect(page.locator('#examSummaryUnavailableHint')).toBeVisible({ timeout: 20000 });
        await expect(page.locator('#showExamSummaryButton')).toBeHidden();
        await expect(page.locator('#exportToPDFButton')).toBeHidden();

        // The server rejects the summary request while the publication date is still in the future.
        const summaryResponse = await page.request.get(`api/exam/courses/${course.id}/exams/${exam.id}/student-exams/${studentExamId}/summary`);
        expect(summaryResponse.status()).toBe(403);
    });

    test.afterEach('Delete exam', async ({ login, examAPIRequests }) => {
        await login(admin);
        await examAPIRequests.deleteExam(exam);
    });
});
