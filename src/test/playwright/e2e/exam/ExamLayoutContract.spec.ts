import { expect } from '@playwright/test';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExerciseType, ProgrammingLanguage } from '../../support/constants';
import {
    COURSE_SHELL_MIN_WIDTH,
    EXAM_BAR_HEIGHT,
    SMALL_BUTTON_HEIGHT,
    expectExamSummaryLayout,
    expectPageNotToScrollSideways,
    expectTitleRow,
    visibleTitleRow,
} from '../../support/examLayoutAssertions';
import { test } from '../../support/fixtures';
import { expectAligned, expectHeight, expectSameValue, forEachViewport, measure } from '../../support/layout';
import { SEED_COURSES } from '../../support/seedData';
import { admin, studentTwo } from '../../support/users';

const course = { id: SEED_COURSES.examParticipation.id } as any;

/** One exercise of every type, in the order the sidebar lists them after the overview. Only some of the pages carry the save button in their title row. */
const EXERCISES = [
    { type: ExerciseType.TEXT, saveButton: true },
    { type: ExerciseType.MODELING, saveButton: true },
    { type: ExerciseType.PROGRAMMING, saveButton: false },
    { type: ExerciseType.QUIZ, saveButton: true },
    { type: ExerciseType.FILE_UPLOAD, saveButton: false },
];

/**
 * The layout contract of the student exam: what has to look the same on every page, wherever the student is in the exam and however wide
 * the window is. It measures instead of comparing screenshots, so a broken rule fails with the element, the viewport and the numbers.
 * <p>
 * Every page of a running exam starts with the same 40px title row, which keeps its position when the student switches pages. The exam
 * bar is 40px, the sidebar and the content end on the same line, and none of it scrolls sideways. The hand-in page and the summary
 * keep the same title row, and the summary scroller ends inside the window. See the section on layout contract tests in the E2E documentation.
 */
test.describe('Exam layout contract', { tag: '@slow' }, () => {
    let exam: Exam;
    let groupTitles: string[];

    test.beforeEach('Create an exam with one exercise of every type', async ({ login, examAPIRequests, examExerciseGroupCreation }) => {
        // Five exercises are created through the API (the programming exercise sets up its repositories), the summary button counts down
        // for 10 s, and every page is checked at five window sizes.
        test.slow();
        await login(admin);
        exam = await examAPIRequests.createRunningExam({ course, examMaxPoints: 50, numberOfExercisesInExam: EXERCISES.length });
        groupTitles = [];
        for (const { type } of EXERCISES) {
            // The exercises are only created: the layout does not depend on a build, so the programming exercise is neither built nor submitted to.
            const additionalData = type === ExerciseType.PROGRAMMING ? { programmingLanguage: ProgrammingLanguage.C } : {};
            const exercise = await examExerciseGroupCreation.handleAddGroupWithExercise(exam, `Layout ${type}`, type, additionalData);
            groupTitles.push(exercise!.exerciseGroup!.title!);
        }
        await examAPIRequests.registerStudentForExam(exam, studentTwo);
        await examAPIRequests.generateMissingIndividualExams(exam);
        await examAPIRequests.prepareExerciseStartForExam(exam);
    });

    test.afterEach('Delete exam', async ({ login, examAPIRequests }) => {
        await login(admin);
        await examAPIRequests.deleteExam(exam);
    });

    test('The welcome page, every page of the running exam, the hand-in page and the summary keep the layout contract', async ({
        page,
        examParticipation,
        examNavigation,
        examStartEnd,
    }) => {
        await examParticipation.openExam(studentTwo, course, exam);

        await test.step('Welcome page', () =>
            forEachViewport(page, async (viewport) => {
                await expectTitleRow(page.getByTestId('exam-start-header'), page.getByTestId('exam-start-title'), 'the welcome page');
                await expectHeight(page.getByTestId('start-exam'), SMALL_BUTTON_HEIGHT, { name: 'start button' });
                if (viewport.width >= COURSE_SHELL_MIN_WIDTH) {
                    await expectPageNotToScrollSideways(page, page.getByTestId('exam-start-header'));
                }
            }));

        await examStartEnd.startExam(true);
        await expect(page.getByTestId('hand-in-early')).toBeVisible();

        // The sidebar lists the overview first. Its cards are reached by position because a collapsed sidebar shows no titles.
        const pages = [
            { name: 'overview', groupTitle: undefined, saveButton: false },
            ...EXERCISES.map(({ type, saveButton }, index) => ({ name: type, groupTitle: groupTitles[index], saveButton })),
        ];

        const sidebar = page.getByTestId('exam-sidebar');
        await test.step('Pages of the running exam', () =>
            forEachViewport(page, async () => {
                await expectHeight(page.getByTestId('exam-bar'), EXAM_BAR_HEIGHT, { name: 'exam bar' });
                await expectHeight(page.getByTestId('hand-in-early'), SMALL_BUTTON_HEIGHT, { name: 'hand-in early button of the exam bar' });
                await expectAligned([sidebar, page.getByTestId('exam-content')], 'bottom', { name: 'the sidebar card and the content card' });
                await expectAligned([page.getByTestId('exam-sidebar-footer'), page.getByTestId('exam-status-bar')], 'top', {
                    name: 'the foot of the sidebar and the connection status bar',
                });

                const tops: Record<string, number> = {};
                for (const [index, examPage] of pages.entries()) {
                    await test.step(`Page ${examPage.name}`, async () => {
                        await sidebar.getByTestId('sidebar-card-title').nth(index).click();
                        const row = visibleTitleRow(page);
                        const title = row.getByTestId('exam-exercise-title');
                        // The page that was open before stays visible until the client has switched, so wait for something that only the new page has.
                        if (examPage.groupTitle) {
                            await expect(title).toContainText(examPage.groupTitle);
                        } else {
                            await expect(page.getByTestId('exercise-table')).toBeVisible();
                        }
                        await expect(row, `the ${examPage.name} page is the only page that is open`).toHaveCount(1);

                        await expectTitleRow(row, title, `the ${examPage.name} page`);
                        tops[examPage.name] = (await measure(row)).top;

                        const saveButton = row.getByTestId('exam-save-button');
                        if (examPage.saveButton) {
                            await expectHeight(saveButton, SMALL_BUTTON_HEIGHT, { name: `save button of the ${examPage.name} page` });
                        } else {
                            await expect(saveButton, `the ${examPage.name} page has no save button in its title row`).toHaveCount(0);
                        }
                        await expectPageNotToScrollSideways(page, row);
                    });
                }
                expectSameValue(page, 'top of the title row on the pages of the exam', tops);
            }));

        await examNavigation.handInEarly();

        await test.step('Hand-in page', () =>
            forEachViewport(page, async () => {
                await expectHeight(page.getByTestId('exam-bar'), EXAM_BAR_HEIGHT, { name: 'exam bar' });
                await expectTitleRow(page.getByTestId('exam-finished-header'), page.getByTestId('exam-finished-title'), 'the hand-in page');
                await expectAligned([page.getByTestId('exam-finished-title'), page.getByTestId('exam-bar-title')], 'left', {
                    name: 'the title of the hand-in page and the title of the exam bar',
                });
                await expectHeight(page.getByTestId('end-exam'), SMALL_BUTTON_HEIGHT, { name: 'hand-in button' });
                await expectPageNotToScrollSideways(page, page.getByTestId('exam-finished-header'));
            }));

        expect((await examStartEnd.finishExam()).status()).toBe(200);

        await examStartEnd.pressShowSummary();

        await test.step('Summary', () => forEachViewport(page, (viewport) => expectExamSummaryLayout(page, viewport)));
    });
});
