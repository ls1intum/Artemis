import { expect, Page } from '@playwright/test';
import { test } from '../../support/fixtures';
import { admin, studentOne } from '../../support/users';
import { SEED_COURSES } from '../../support/seedData';
import { ProgrammingLanguage } from '../../support/constants';
import dayjs from 'dayjs';
import { Exam } from 'app/exam/shared/entities/exam.model';

const course = { id: SEED_COURSES.examParticipation.id } as any;

/**
 * The repository of a student is locked outside the conduction of the exam: it can not be written to before the exam starts, it is unlocked at the
 * start, and it is locked again when the exam is over. The test writes through the server the way the online editor does, so the rule is checked on the
 * server, which does not depend on what the client shows.
 */
test.describe('Exam programming repository lock', { tag: '@slow' }, () => {
    let exam: Exam;

    test.afterEach('Delete exam', async ({ login, examAPIRequests }) => {
        await login(admin);
        await examAPIRequests.deleteExam(exam);
    });

    /** Writes the given source file the way the online editor does and returns the status of the server. */
    async function writeSourceFile(page: Page, participationId: number, sourceFile: string): Promise<number> {
        const response = await page.request.put(`api/programming/repository/${participationId}/files?commit=false`, {
            data: [{ fileName: sourceFile, fileContent: 'int main() { return 0; }\n' }],
        });
        return response.status();
    }

    test('The repository of the student is locked before the start, unlocked during the exam and locked after it', async ({
        page,
        login,
        examAPIRequests,
        exerciseAPIRequests,
    }) => {
        test.slow();
        await login(admin);
        exam = await examAPIRequests.createExam({
            course,
            visibleDate: dayjs().subtract(1, 'day'),
            startDate: dayjs().add(2, 'days'),
            endDate: dayjs().add(3, 'days'),
        });
        const exerciseGroup = await examAPIRequests.addExerciseGroupForExam(exam);
        const exercise = await exerciseAPIRequests.createProgrammingExercise({ exerciseGroup, programmingLanguage: ProgrammingLanguage.C });
        await examAPIRequests.registerStudentForExam(exam, studentOne);
        await examAPIRequests.generateMissingIndividualExams(exam);
        await examAPIRequests.prepareExerciseStartForExam(exam);
        const participation = await exerciseAPIRequests.getProgrammingExerciseParticipation(exercise.id!);
        const participationId = participation.id!;
        const files = (await (await page.request.get(`api/programming/participations/${participationId}/repository/files`)).json()) as Record<string, string>;
        const sourceFile = Object.keys(files).find((name) => files[name] === 'FILE' && name.endsWith('.c'));
        expect(sourceFile, `the repository has a source file, but holds: ${Object.keys(files).join(', ')}`).toBeDefined();

        // Two days before the start the repository is locked.
        await login(studentOne);
        expect(await writeSourceFile(page, participationId, sourceFile!), 'the repository is locked before the exam starts').toBe(403);

        // The exam starts in a moment: the repository is unlocked by then.
        await login(admin);
        const start = await examAPIRequests.rescheduleExam(exam, 15, 60 * 60);
        await examAPIRequests.waitUntilServerClockIsAfter(exam, start);
        await login(studentOne);
        await expect
            .poll(() => writeSourceFile(page, participationId, sourceFile!), {
                message: 'the repository is unlocked once the exam has started',
                timeout: 90_000,
                intervals: [2000],
            })
            .toBe(200);

        // The exam ends: the repository is locked again.
        await login(admin);
        await examAPIRequests.finishExam(exam);
        await login(studentOne);
        await expect
            .poll(() => writeSourceFile(page, participationId, sourceFile!), { message: 'the repository is locked again after the exam', timeout: 90_000, intervals: [2000] })
            .toBe(403);
    });
});
