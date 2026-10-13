import fs from 'fs';
import { expect } from '@playwright/test';
import { test } from '../../support/fixtures';
import { admin, studentOne } from '../../support/users';
import { getExercise } from '../../support/utils';
import { SEED_COURSES } from '../../support/seedData';
import { ExerciseType, ProgrammingLanguage } from '../../support/constants';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { Fixtures } from '../../fixtures/fixtures';
import cAllSuccessfulSubmission from '../../fixtures/exercise/programming/c/all_successful/submission.json';

const course = { id: SEED_COURSES.examParticipation.id } as any;
const fileUploadFixture = 'pdf-test-file.pdf';

/**
 * The submission overview of a handed-in exam, for the exercise types that are not covered by the text, quiz and modeling tests: what the
 * overview shows for a file upload and a programming exercise is what the server stored for the student.
 */
test.describe('Exam summary of file upload and programming exercises', { tag: '@slow' }, () => {
    let exam: Exam;

    test.afterEach('Delete exam', async ({ login, examAPIRequests }) => {
        await login(admin);
        await examAPIRequests.deleteExam(exam);
    });

    test('The submission overview shows the uploaded file and the commit of the programming submission', async ({
        page,
        login,
        examAPIRequests,
        examExerciseGroupCreation,
        examParticipation,
        examNavigation,
        examStartEnd,
    }) => {
        test.slow();
        await login(admin);
        exam = await examAPIRequests.createRunningExam({ course, examMaxPoints: 20, numberOfExercisesInExam: 2 });
        const fileUploadExercise = await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.FILE_UPLOAD, { fileUploadFixture });
        const programmingExercise = await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.PROGRAMMING, {
            submission: cAllSuccessfulSubmission,
            programmingLanguage: ProgrammingLanguage.C,
            // The build of the submission is not what this test is about; only the commit it leads to.
            skipBuildResultCheck: true,
        });
        await examAPIRequests.registerStudentForExam(exam, studentOne);
        await examAPIRequests.generateMissingIndividualExams(exam);
        await examAPIRequests.prepareExerciseStartForExam(exam);

        await examParticipation.startParticipation(studentOne, course, exam);
        for (const exercise of [fileUploadExercise, programmingExercise]) {
            await examNavigation.openOrSaveExerciseByTitle(exercise.exerciseGroup!.title!);
            await examParticipation.makeSubmission(exercise.id!, exercise.type!, exercise.additionalData);
        }
        await examParticipation.handInEarly();
        await examStartEnd.pressShowSummary();

        const summary = await examAPIRequests.getOwnStudentExamSummary(exam);
        expect(summary.submitted).toBe(true);
        expect(summary.exercises).toHaveLength(2);
        const submissionOf = (exerciseId: number) => summary.exercises!.find((candidate) => candidate.id === exerciseId)!.studentParticipations![0].submissions![0] as any;

        // File upload: the overview names the uploaded file, and the server hands out exactly the file that was uploaded.
        const fileSubmission = submissionOf(fileUploadExercise.id!);
        expect(fileSubmission.filePath, 'the server stored the file').toContain(fileUploadFixture);
        await expect(getExercise(page, fileUploadExercise.id!)).toContainText(fileUploadFixture);
        const download = await page.request.get(`api/core/files/${fileSubmission.filePath}`);
        expect(download.status()).toBe(200);
        expect((await download.body()).length).toBe(fs.statSync(Fixtures.getAbsoluteFilePath(fileUploadFixture)).size);

        // Programming: the overview links the submission to the commit the server recorded, not to "no commit".
        const programmingSubmission = submissionOf(programmingExercise.id!);
        expect(programmingSubmission.commitHash, 'the server recorded the commit of the submission').toMatch(/^[0-9a-f]{40}$/);
        // The overview abbreviates the commit hash to its first eleven characters.
        await expect(getExercise(page, programmingExercise.id!)).toContainText(programmingSubmission.commitHash.substring(0, 11));
        await expect(getExercise(page, programmingExercise.id!)).not.toContainText('No commit hash');
    });
});
