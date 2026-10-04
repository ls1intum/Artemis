import { expect, Browser, Page } from '@playwright/test';
import dayjs from 'dayjs';
import { test } from '../../support/fixtures';
import { admin, studentOne } from '../../support/users';
import { generateUUID, newBrowserPage } from '../../support/utils';
import { Fixtures } from '../../fixtures/fixtures';
import { SEED_COURSES } from '../../support/seedData';
import { Commands } from '../../support/commands';
import { ExamAPIRequests } from '../../support/requests/ExamAPIRequests';
import { ExerciseAPIRequests } from '../../support/requests/ExerciseAPIRequests';
import { ModalDialogBox } from '../../support/pageobjects/exam/ModalDialogBox';
import { Exam } from 'app/exam/shared/entities/exam.model';

const course = { id: SEED_COURSES.examManagement.id } as any;

test.describe('Exam date verification', { tag: '@fast' }, () => {
    let exams: Exam[];

    test.beforeEach(async ({ login }) => {
        exams = [];
        await login(admin);
    });

    /** Browser contexts opened by the test, closed again however the test ends. */
    let openedPages: Page[] = [];
    async function openPage(browser: Browser): Promise<Page> {
        const page = await newBrowserPage(browser);
        openedPages.push(page);
        return page;
    }

    test.afterEach('Delete exams', async ({ login, examAPIRequests }) => {
        for (const openedPage of openedPages) {
            await openedPage.context().close();
        }
        openedPages = [];
        // The tests end as a student, who is not allowed to delete an exam.
        await login(admin);
        for (const exam of exams) {
            await examAPIRequests.deleteExam(exam);
        }
    });

    /**
     * Creates an exam whose dates are given relative to now, optionally with one text exercise, registers the student and prepares
     * their student exam. The exercise must exist before the registration: for an exam that has already started the server
     * generates the student exam of a newly registered student right away, and that student exam would stay empty.
     */
    async function createPreparedExam(
        examAPIRequests: ExamAPIRequests,
        exerciseAPIRequests: ExerciseAPIRequests,
        dates: { visible: dayjs.Dayjs; start: dayjs.Dayjs; end: dayjs.Dayjs },
        withTextExercise = true,
    ): Promise<Exam> {
        const exam = await examAPIRequests.createExam({
            course,
            title: 'exam' + generateUUID(),
            visibleDate: dates.visible,
            startDate: dates.start,
            endDate: dates.end,
        });
        exams.push(exam);
        if (withTextExercise) {
            const exerciseGroup = await examAPIRequests.addExerciseGroupForExam(exam);
            await exerciseAPIRequests.createTextExercise({ exerciseGroup });
        }
        await examAPIRequests.registerStudentForExam(exam, studentOne);
        await examAPIRequests.generateMissingIndividualExams(exam);
        await examAPIRequests.prepareExerciseStartForExam(exam);
        return exam;
    }

    test.describe('Exam timing', () => {
        test('Does not show exam before visible date', async ({ page, login, examAPIRequests, exerciseAPIRequests, courseOverview }) => {
            const hiddenExam = await createPreparedExam(examAPIRequests, exerciseAPIRequests, {
                visible: dayjs().add(1, 'day'),
                start: dayjs().add(2, 'days'),
                end: dayjs().add(3, 'days'),
            });
            // A second exam that is already visible makes the absence of the first one meaningful: the same student sees the exam list, just not this exam.
            const visibleExam = await createPreparedExam(examAPIRequests, exerciseAPIRequests, {
                visible: dayjs().subtract(1, 'day'),
                start: dayjs().add(2, 'days'),
                end: dayjs().add(3, 'days'),
            });

            await login(studentOne);
            await page.goto(`/courses/${course.id}`);
            await courseOverview.openExamsTab();
            await expect(page.getByText(visibleExam.title!).first()).toBeVisible();
            await expect(page.getByText(hiddenExam.title!)).toHaveCount(0);

            // The client is not the only guard: the server hides the exam from the overview and refuses to hand out the student exam.
            const overview = await page.request.get(`api/exam/courses/${course.id}/exams-for-overview`);
            expect(overview.ok()).toBe(true);
            const overviewIds = ((await overview.json()) as { id: number }[]).map((exam) => exam.id);
            expect(overviewIds).toContain(visibleExam.id);
            expect(overviewIds).not.toContain(hiddenExam.id);
            const ownStudentExam = await page.request.get(`api/exam/courses/${course.id}/exams/${hiddenExam.id}/own-student-exam`);
            expect(ownStudentExam.status()).toBe(403);
        });

        test('Shows after visible date but does not let the student start before the start window', async ({
            page,
            login,
            examAPIRequests,
            exerciseAPIRequests,
            courseOverview,
            examStartEnd,
        }) => {
            const exam = await createPreparedExam(examAPIRequests, exerciseAPIRequests, {
                visible: dayjs().subtract(5, 'days'),
                start: dayjs().add(2, 'days'),
                end: dayjs().add(3, 'days'),
            });

            await login(studentOne);
            await page.goto(`/courses/${course.id}`);
            await courseOverview.openExamsTab();
            // The course is shared with other tests, so the exam is opened from the list rather than relying on it being the only one.
            await page.getByRole('link', { name: exam.title! }).click();
            await page.waitForURL(`**/exams/${exam.id}`);
            await expect(page.getByText(exam.title!).first()).toBeVisible();
            // The exam is visible and the student can confirm and enter the name, but two days before its start the exam can not be started.
            await page.locator('#confirmBox').check();
            await examStartEnd.enterFirstnameLastname();
            await expect(page.getByTestId('start-exam')).toBeDisabled();
        });

        test('Student can start after start Date and the saved state matches the server', async ({
            page,
            login,
            examAPIRequests,
            exerciseAPIRequests,
            examStartEnd,
            examNavigation,
            textExerciseEditor,
        }) => {
            const exam = await createPreparedExam(examAPIRequests, exerciseAPIRequests, {
                visible: dayjs().subtract(3, 'days'),
                start: dayjs().subtract(2, 'days'),
                end: dayjs().add(3, 'days'),
            });
            const [exerciseGroup] = await examAPIRequests.getExerciseGroups(exam);
            const exercise = exerciseGroup.exercises![0];

            await login(studentOne, `/courses/${course.id}/exams/${exam.id}`);
            await page.waitForURL(`**/exams/${exam.id}`);
            await expect(page.getByText(exam.title!).first()).toBeVisible();
            // Before the student confirms and enters their name, starting is impossible.
            await expect(page.getByTestId('start-exam')).toBeDisabled();
            await examStartEnd.startExam();

            // Conduction view: exam title, running timer and the hand-in button are there.
            await expect(page.getByTestId('exam-bar-title')).toContainText(exam.title!);
            await expect(page.getByTestId('displayTime')).toHaveText(/\S/);
            await expect(page.getByTestId('hand-in-early')).toBeVisible();

            const saveState = page.getByTestId('sidebar-exercise-status');
            await expect(saveState).toHaveAttribute('data-status', 'synced');
            await saveState.hover();
            await expect(page.getByRole('tooltip')).toContainText('Exercise not started');
            await examNavigation.openOrSaveExerciseByTitle(exerciseGroup.title!);
            const submission = (await Fixtures.get('loremIpsum-short.txt'))!;
            await textExerciseEditor.typeSubmission(exercise.id!, submission);

            await expect(saveState).toHaveAttribute('data-status', 'notSynced');
            await saveState.hover();
            await expect(page.getByRole('tooltip')).toContainText('Exercise not saved');
            await examNavigation.openOrSaveExerciseByTitle(exerciseGroup.title!);

            await expect(saveState).toHaveAttribute('data-status', 'synced saved');
            await saveState.hover();
            await expect(page.getByRole('tooltip')).toContainText('Exercise saved');

            // "Saved" must mean saved: the server holds exactly what the student typed.
            const conduction = await examAPIRequests.getOwnStudentExamForConduction(exam);
            const savedSubmission = conduction.exercises!.find((candidate) => candidate.id === exercise.id)!.studentParticipations![0].submissions![0] as { text?: string };
            expect(savedSubmission.text).toBe(submission);
        });

        test('Exam ends after end time: the student is taken to the end view and the work is kept', async ({
            page,
            browser,
            login,
            examAPIRequests,
            exerciseAPIRequests,
            examStartEnd,
            examNavigation,
            examParticipation,
            textExerciseEditor,
        }) => {
            // A long exam: the deadline is only set once the student is working, so a slow setup cannot eat into it.
            const exam = await createPreparedExam(examAPIRequests, exerciseAPIRequests, {
                visible: dayjs().subtract(1, 'day'),
                start: dayjs().subtract(1, 'minute'),
                end: dayjs().add(2, 'hours'),
            });
            const [exerciseGroup] = await examAPIRequests.getExerciseGroups(exam);
            const exercise = exerciseGroup.exercises![0];

            await login(studentOne, `/courses/${course.id}/exams/${exam.id}`);
            await page.waitForURL(`**/exams/${exam.id}`);
            await examStartEnd.startExam();
            await examNavigation.openOrSaveExerciseByTitle(exerciseGroup.title!);
            const submission = (await Fixtures.get('loremIpsum-short.txt'))!;
            await textExerciseEditor.typeSubmission(exercise.id!, submission);
            await examNavigation.openOrSaveExerciseByTitle(exerciseGroup.title!);
            await expect(page.getByTestId('sidebar-exercise-status')).toHaveAttribute('data-status', 'synced saved');

            // With two hours left the timer is calm.
            await expect(page.getByTestId('displayTime')).toHaveAttribute('data-critical', 'false');

            // The instructor side ends the exam shortly from now. The student learns about it through the working-time live event.
            const adminPage = await openPage(browser);
            await Commands.login(adminPage, admin);
            const adminExamRequests = new ExamAPIRequests(adminPage);
            await adminExamRequests.endExamIn(exam, 20);

            const workingTimeDialog = new ModalDialogBox(page);
            await workingTimeDialog.checkDialogMessage('The working time of the exam has been changed.');
            await workingTimeDialog.closeDialog();
            // Twenty seconds are left: the timer says so.
            await expect(page.getByTestId('displayTime')).toHaveAttribute('data-critical', 'true');

            // The exam runs out on its own: the client leaves the exercise and asks the student to hand in.
            await examParticipation.checkExamFinishedTitle(exam.title!);
            const response = await examStartEnd.finishExam();
            expect(response.status()).toBe(200);

            // The server agrees: the exam counts as submitted ...
            const studentExams = await adminExamRequests.getAllStudentExams(exam);
            expect(studentExams).toHaveLength(1);
            expect(studentExams[0].submitted).toBe(true);
            // ... and the student's summary still shows what they typed before the exam ran out.
            await examStartEnd.pressShowSummary();
            await examParticipation.verifyTextExerciseOnFinalPage(exercise.id!, 'loremIpsum-short.txt');
            await examParticipation.checkExamTitle(exam.title!);
        });
    });
});
