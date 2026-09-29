import { expect } from '@playwright/test';
import { test } from '../../support/fixtures';
import { admin, studentOne, studentTwo } from '../../support/users';
import { newBrowserPage } from '../../support/utils';
import { SEED_COURSES } from '../../support/seedData';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { Commands } from '../../support/commands';
import { ExamStartEndPage } from '../../support/pageobjects/exam/ExamStartEndPage';
import { ModalDialogBox } from '../../support/pageobjects/exam/ModalDialogBox';

const course = { id: SEED_COURSES.examParticipation.id } as any;

/**
 * The running exam of createRunningExam started two minutes ago and lasts 62 minutes, so every student has a working time of
 * "1h 2min" and roughly an hour left.
 */
test.describe('Exam working time', { tag: '@slow' }, () => {
    let exam: Exam;

    test.afterEach('Delete exam', async ({ login, examAPIRequests }) => {
        await login(admin);
        await examAPIRequests.deleteExam(exam);
    });

    test('An individual extension reaches only that student, and a later change of the whole exam rescales it', async ({
        browser,
        login,
        examAPIRequests,
        exerciseAPIRequests,
    }) => {
        await login(admin);
        exam = await examAPIRequests.createRunningExam({ course });
        const exerciseGroup = await examAPIRequests.addExerciseGroupForExam(exam);
        await exerciseAPIRequests.createTextExercise({ exerciseGroup });
        await examAPIRequests.registerStudentForExam(exam, studentOne);
        await examAPIRequests.registerStudentForExam(exam, studentTwo);
        await examAPIRequests.generateMissingIndividualExams(exam);
        await examAPIRequests.prepareExerciseStartForExam(exam);

        // Both students are taking the exam, each on a page of their own.
        const [extendedStudentPage, otherStudentPage] = [await newBrowserPage(browser), await newBrowserPage(browser)];
        for (const [studentPage, student] of [
            [extendedStudentPage, studentOne],
            [otherStudentPage, studentTwo],
        ] as const) {
            await Commands.login(studentPage, student);
            await studentPage.goto(`/courses/${course.id}/exams/${exam.id}`);
            await new ExamStartEndPage(studentPage).startExam();
            await expect(studentPage.getByTestId('displayTime')).toContainText('59min');
        }
        // The listing of student exams does not say whose is whose, so the extended student's own page tells which one it is.
        const ownStudentExam = await extendedStudentPage.request.get(`api/exam/courses/${course.id}/exams/${exam.id}/own-student-exam`);
        const extendedStudentExamId: number = (await ownStudentExam.json()).id;
        const studentExams = await examAPIRequests.getAllStudentExams(exam);
        expect(studentExams.map((studentExam: StudentExam) => studentExam.workingTime)).toEqual([3720, 3720]);

        // 1. The instructor gives one student ten more minutes.
        const updated = await examAPIRequests.setStudentExamWorkingTime(exam, extendedStudentExamId, 3720 + 600);
        expect(updated.workingTime).toBe(4320);
        const extendedDialog = new ModalDialogBox(extendedStudentPage);
        await expect(extendedDialog.getModalDialogContent()).toContainText('Your personal working time of the exam has been changed.');
        await extendedDialog.checkExamTimeChangeDialog('1h 2min', '1h 12min');
        await extendedDialog.closeDialog();
        // The timer follows: about an hour and nine minutes are left for this student, still about 59 minutes for the other one.
        await expect(extendedStudentPage.getByTestId('displayTime')).toContainText('1h');
        await expect(otherStudentPage.getByTestId('displayTime')).toContainText('59min');
        await expect(otherStudentPage.getByTestId('displayTime')).not.toContainText('1h');
        await expect(new ModalDialogBox(otherStudentPage).getModalDialogContent()).toHaveCount(0);

        // 2. The instructor then extends the whole exam by five minutes: it reaches the other student, and the individually extended student
        //    keeps the extension on top of it.
        await examAPIRequests.changeExamWorkingTime(exam, 300);
        const otherDialog = new ModalDialogBox(otherStudentPage);
        await expect(otherDialog.getModalDialogContent()).toContainText('The working time of the exam has been changed.');
        await otherDialog.checkExamTimeChangeDialog('1h 2min', '1h 7min');
        await otherDialog.closeDialog();
        await extendedDialog.checkExamTimeChangeDialog('1h 12min', '1h 17min');
        await extendedDialog.closeDialog();

        // The server holds the working times the students were told. The extension of the individually extended student is not a fixed
        // number of minutes: it scales with the exam, so the ten extra minutes on 62 minutes become 4320 * 4020 / 3720 seconds on 67 minutes.
        const afterwards: StudentExam[] = await examAPIRequests.getAllStudentExams(exam);
        expect(afterwards.find((studentExam) => studentExam.id === extendedStudentExamId)!.workingTime).toBe(Math.round((4320 * 4020) / 3720));
        expect(afterwards.find((studentExam) => studentExam.id !== extendedStudentExamId)!.workingTime).toBe(4020);
    });
});
