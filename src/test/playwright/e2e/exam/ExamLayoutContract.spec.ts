import { expect } from '@playwright/test';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExerciseType, ProgrammingLanguage } from '../../support/constants';
import {
    COURSE_SHELL_MIN_WIDTH,
    EXAM_BAR_HEIGHT,
    SMALL_BUTTON_HEIGHT,
    expectExamBarInsets,
    expectExamContentInsets,
    expectExamSummaryLayout,
    expectFileBrowserDividerResizes,
    expectFileUploadRow,
    expectHandInInsets,
    expectPageNotToScrollSideways,
    expectProblemStatementDivider,
    expectProblemStatementDividerResizes,
    expectProblemStatementDividerWhileCollapsed,
    expectProgrammingActions,
    expectProgrammingPanelDividers,
    expectStartViewInsets,
    expectTitleRow,
    expectTitleRowButton,
    visibleTitleRow,
} from '../../support/examLayoutAssertions';
import { test } from '../../support/fixtures';
import { expectAligned, expectHeight, expectSameValue, forEachViewport, measure } from '../../support/layout';
import { SEED_COURSES } from '../../support/seedData';
import { admin, studentTwo } from '../../support/users';

const course = { id: SEED_COURSES.examParticipation.id } as any;

/** One exercise of every type, in the order the sidebar lists them after the overview. Only some of the pages carry the save button in their title row; the programming page carries its own actions there. */
const EXERCISES = [
    { type: ExerciseType.TEXT, saveButton: true },
    { type: ExerciseType.MODELING, saveButton: true },
    { type: ExerciseType.PROGRAMMING, saveButton: false },
    { type: ExerciseType.QUIZ, saveButton: true },
    { type: ExerciseType.FILE_UPLOAD, saveButton: false },
];

/** The pages that show the problem statement next to the solution, in the shared resizeable container. The quiz and the programming page have their own layout. */
const PAGES_WITH_PROBLEM_STATEMENT = [ExerciseType.TEXT, ExerciseType.MODELING, ExerciseType.FILE_UPLOAD];

/** The group of the programming exercise has a title that is longer than the room next to its actions, so that the page has to cut the title off instead of pushing the actions out of their row. */
const LONG_GROUP_TITLE_SUFFIX = ' with a group title that is much longer than the room next to its actions';

/**
 * The layout contract of the student exam: what has to look the same on every page, wherever the student is in the exam and however wide
 * the window is. It measures instead of comparing screenshots, so a broken rule fails with the element, the viewport and the numbers.
 * <p>
 * Every page of a running exam starts with the same 40px title row, which keeps its position and the left edge of its title when the
 * student switches pages. A card keeps 12px to what is in it on every side: the title row to the left, top and right edge, the end of the
 * page to the bottom edge, and in the exam bar the title and the hand-in early button to the left and right edge. A button in a row is 30px
 * high with air above and below it; on the programming page the Code button, the result, Refresh and Submit are in the row where there is
 * room, and below it where there is not. The exam bar is 40px, the sidebar and the content end on the same line, and none of it scrolls
 * sideways. The panels of a page are set apart by the 6px divider of the application: the solution and the problem statement of the text,
 * modeling and file upload page, also while the problem statement is collapsed, and the file browser, the editor, the instructions and the
 * build output of the programming page; the dividers can still be dragged. The file input of the file upload page and its Upload button are
 * one row where the panel has room. The hand-in page and the summary keep the same title row and insets, and the summary scroller ends inside
 * the window. See the section on layout contract tests in the E2E documentation.
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
            const exercise = await examExerciseGroupCreation.handleAddGroupWithExercise(
                exam,
                `Layout ${type}${type === ExerciseType.PROGRAMMING ? LONG_GROUP_TITLE_SUFFIX : ''}`,
                type,
                additionalData,
            );
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
                await expectStartViewInsets(page);
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
        const openExamPage = async (index: number) => {
            const examPage = pages[index];
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
            return { row, title };
        };
        await test.step('Pages of the running exam', () =>
            forEachViewport(page, async (viewport) => {
                await expectHeight(page.getByTestId('exam-bar'), EXAM_BAR_HEIGHT, { name: 'exam bar' });
                await expectExamBarInsets(page);
                await expectAligned([sidebar, page.getByTestId('exam-content')], 'bottom', { name: 'the sidebar card and the content card' });
                await expectAligned([page.getByTestId('exam-sidebar-footer'), page.getByTestId('exam-status-bar')], 'top', {
                    name: 'the foot of the sidebar and the connection status bar',
                });

                const tops: Record<string, number> = {};
                const lefts: Record<string, number> = {};
                for (const [index, examPage] of pages.entries()) {
                    await test.step(`Page ${examPage.name}`, async () => {
                        const { row, title } = await openExamPage(index);

                        await expectTitleRow(row, title, `the ${examPage.name} page`);
                        await expectExamContentInsets(page, row, title, `the ${examPage.name} page`);
                        tops[examPage.name] = (await measure(row)).top;
                        lefts[examPage.name] = (await measure(title)).left;

                        const saveButton = row.getByTestId('exam-save-button');
                        if (examPage.saveButton) {
                            await expectTitleRowButton(saveButton, row, `save button of the ${examPage.name} page`);
                        } else {
                            await expect(saveButton, `the ${examPage.name} page has no save button in its title row`).toHaveCount(0);
                        }
                        if (examPage.name === ExerciseType.PROGRAMMING) {
                            await expectProgrammingActions(page, row, viewport);
                            await expectProgrammingPanelDividers(page);
                        }
                        if (PAGES_WITH_PROBLEM_STATEMENT.includes(examPage.name as ExerciseType)) {
                            await expectProblemStatementDivider(page, `the ${examPage.name} page`);
                        }
                        if (examPage.name === ExerciseType.FILE_UPLOAD) {
                            await expectFileUploadRow(page, viewport);
                        }
                        await expectPageNotToScrollSideways(page, row);
                    });
                }
                expectSameValue(page, 'top of the title row on the pages of the exam', tops);
                expectSameValue(page, 'left edge of the title on the pages of the exam', lefts);
            }));

        // The dividers keep working as handles, and the collapsed problem statement keeps its distance. A divider that was dragged leaves the width
        // it was given in px, which a smaller window does not undo, so this is left to the end of the pages, at the size of the window the test started with.
        await test.step('Dividers can be dragged and the problem statement collapsed', async () => {
            await openExamPage(pages.findIndex(({ name }) => name === ExerciseType.TEXT));
            await expectProblemStatementDividerWhileCollapsed(page, 'the text page');
            await expectProblemStatementDividerResizes(page, 'the text page');
            await openExamPage(pages.findIndex(({ name }) => name === ExerciseType.PROGRAMMING));
            await expectFileBrowserDividerResizes(page);
        });

        await examNavigation.handInEarly();

        await test.step('Hand-in page', () =>
            forEachViewport(page, async () => {
                await expectHeight(page.getByTestId('exam-bar'), EXAM_BAR_HEIGHT, { name: 'exam bar' });
                await expectTitleRow(page.getByTestId('exam-finished-header'), page.getByTestId('exam-finished-title'), 'the hand-in page');
                await expectAligned([page.getByTestId('exam-finished-title'), page.getByTestId('exam-bar-title')], 'left', {
                    name: 'the title of the hand-in page and the title of the exam bar',
                });
                await expectHandInInsets(page);
                await expectHeight(page.getByTestId('end-exam'), SMALL_BUTTON_HEIGHT, { name: 'hand-in button' });
                await expectPageNotToScrollSideways(page, page.getByTestId('exam-finished-header'));
            }));

        expect((await examStartEnd.finishExam()).status()).toBe(200);

        await examStartEnd.pressShowSummary();

        await test.step('Summary', () => forEachViewport(page, (viewport) => expectExamSummaryLayout(page, viewport)));
    });
});
