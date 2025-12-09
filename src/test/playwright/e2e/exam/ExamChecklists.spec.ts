import { test } from '../../support/fixtures';
import { admin, instructor, studentOne } from '../../support/users';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { generateUUID, prepareExam, startAssessing } from '../../support/utils';
import dayjs from 'dayjs';
import { ExamChecklistItem } from '../../support/pageobjects/exam/ExamDetailsPage';
import { ExerciseType } from '../../support/constants';
import textExerciseTemplate from '../../fixtures/exercise/text/template.json';
import { ExamExerciseGroupCreationPage } from '../../support/pageobjects/exam/ExamExerciseGroupCreationPage';
import { ExamExerciseGroupsPage } from '../../support/pageobjects/exam/ExamExerciseGroupsPage';
import { Page } from '@playwright/test';
import { Commands } from '../../support/commands';
import { ExamAPIRequests } from '../../support/requests/ExamAPIRequests';

test.describe('Exam Checklists', async () => {
    let course: Course;

    test.beforeEach('Create course', async ({ login, courseManagementAPIRequests }) => {
        await login(admin);
        course = await courseManagementAPIRequests.createCourse({ customizeGroups: true });
        await courseManagementAPIRequests.addStudentToCourse(course, studentOne);
    });

    test.describe('Exercise group checks', { tag: '@fast' }, () => {
        test('Instructor adds an exercise group and at least one exercise group check is marked', async ({
            page,
            login,
            examDetails,
            examExerciseGroups,
            examExerciseGroupCreation,
        }) => {
            const exam = await createExam(course, page);
            await login(instructor);
            await navigateToExamDetailsPage(page, course, exam);
            await examDetails.checkItemUnchecked(ExamChecklistItem.LEAST_ONE_EXERCISE_GROUP);
            await examDetails.openExerciseGroups();
            await examExerciseGroups.clickAddExerciseGroup();
            await examExerciseGroupCreation.typeTitle('Group 1');
            await examExerciseGroupCreation.clickSave();
            await navigateToExamDetailsPage(page, course, exam);
            await examDetails.checkItemChecked(ExamChecklistItem.LEAST_ONE_EXERCISE_GROUP);
        });

        test('Instructor adds exercise groups and the number of exercise groups check is correctly reacting to changes', async ({
            page,
            login,
            examDetails,
            examExerciseGroups,
            examExerciseGroupCreation,
        }) => {
            const exam = await createExam(course, page);
            await login(instructor);
            await navigateToExamDetailsPage(page, course, exam);
            await examDetails.checkItemUnchecked(ExamChecklistItem.NUMBER_OF_EXERCISE_GROUPS);
            await examDetails.openExerciseGroups();
            for (let i = 0; i < exam.numberOfExercisesInExam!; i++) {
                await addExamExerciseGroup(examExerciseGroups, examExerciseGroupCreation);
            }
            await navigateToExamDetailsPage(page, course, exam);
            await examDetails.openExerciseGroups();
            await addExamExerciseGroup(examExerciseGroups, examExerciseGroupCreation, false);
            await navigateToExamDetailsPage(page, course, exam);
            await examDetails.checkItemChecked(ExamChecklistItem.NUMBER_OF_EXERCISE_GROUPS);
            await examDetails.openExerciseGroups();
            await addExamExerciseGroup(examExerciseGroups, examExerciseGroupCreation);
            await navigateToExamDetailsPage(page, course, exam);
            await examDetails.checkItemUnchecked(ExamChecklistItem.NUMBER_OF_EXERCISE_GROUPS);
        });

        test('Instructor adds exercise groups and each exercise group has exercises check is correctly reacting to changes', async ({
            page,
            login,
            examDetails,
            examExerciseGroups,
            examExerciseGroupCreation,
        }) => {
            const exam = await createExam(course, page);
            await login(instructor);
            await navigateToExamDetailsPage(page, course, exam);
            await examDetails.checkItemUnchecked(ExamChecklistItem.EACH_EXERCISE_GROUP_HAS_EXERCISES);
            await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.TEXT);
            await page.reload();
            await examDetails.checkItemChecked(ExamChecklistItem.EACH_EXERCISE_GROUP_HAS_EXERCISES);
            await examDetails.openExerciseGroups();
            await examExerciseGroups.clickAddExerciseGroup();
            await examExerciseGroupCreation.typeTitle('Empty group');
            await examExerciseGroupCreation.clickSave();
            await navigateToExamDetailsPage(page, course, exam);
            await examDetails.checkItemUnchecked(ExamChecklistItem.EACH_EXERCISE_GROUP_HAS_EXERCISES);
        });

        test('Instructor adds exercise groups and points in exercise groups equal check is correctly reacting to changes', async ({
            page,
            login,
            examDetails,
            examAPIRequests,
            exerciseAPIRequests,
        }) => {
            const exam = await createExam(course, page);
            await login(instructor);
            await navigateToExamDetailsPage(page, course, exam);
            await examDetails.checkItemUnchecked(ExamChecklistItem.POINTS_IN_EXERCISE_GROUPS_EQUAL);
            const exerciseGroup = await examAPIRequests.addExerciseGroupForExam(exam);
            await exerciseAPIRequests.createTextExercise({ exerciseGroup }, 'Exercise ' + generateUUID(), textExerciseTemplate);
            await page.reload();
            await examDetails.checkItemChecked(ExamChecklistItem.POINTS_IN_EXERCISE_GROUPS_EQUAL);
            const maxPointsOfFirstExercise = textExerciseTemplate.maxPoints;
            const exerciseTemplate = Object.assign({}, textExerciseTemplate);
            exerciseTemplate.maxPoints = maxPointsOfFirstExercise - 1;
            await exerciseAPIRequests.createTextExercise({ exerciseGroup }, 'Exercise ' + generateUUID(), exerciseTemplate);
            await page.reload();
            await examDetails.checkItemUnchecked(ExamChecklistItem.POINTS_IN_EXERCISE_GROUPS_EQUAL);
        });

        test('Instructor adds exercise groups and total points possible check is correctly reacting to changes', async ({
            page,
            login,
            examDetails,
            examExerciseGroupCreation,
        }) => {
            const exam = await createExam(course, page);
            await login(instructor);
            await navigateToExamDetailsPage(page, course, exam);
            await examDetails.checkItemUnchecked(ExamChecklistItem.TOTAL_POINTS_POSSIBLE);
            await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.TEXT);
            await page.reload();
            await examDetails.checkItemUnchecked(ExamChecklistItem.TOTAL_POINTS_POSSIBLE);
            await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.TEXT, {}, false);
            await page.reload();
            await examDetails.checkItemChecked(ExamChecklistItem.TOTAL_POINTS_POSSIBLE);
            await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.TEXT);
            await page.reload();
            await examDetails.checkItemChecked(ExamChecklistItem.TOTAL_POINTS_POSSIBLE);
            await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.TEXT);
            await page.reload();
            await examDetails.checkItemUnchecked(ExamChecklistItem.TOTAL_POINTS_POSSIBLE);
        });
    });

    test('Instructor registers a student to exam and at least one student check is marked', { tag: '@fast' }, async ({ page, login, examDetails, studentExamManagement }) => {
        const exam = await createExam(course, page);
        await login(instructor);
        await navigateToExamDetailsPage(page, course, exam);
        await examDetails.checkItemUnchecked(ExamChecklistItem.LEAST_ONE_STUDENT);
        await examDetails.clickStudentsToRegister();
        await studentExamManagement.clickRegisterCourseStudents();
        await navigateToExamDetailsPage(page, course, exam);
        await examDetails.checkItemChecked(ExamChecklistItem.LEAST_ONE_STUDENT);
    });

    test.describe('Individual exam generation and exam preparation checks', { tag: '@fast' }, () => {
        let exam: Exam;

        test.beforeEach('Create exam', async ({ page }) => {
            exam = await createExam(course, page);
        });

        test.beforeEach('Add exercise groups and register exam students', async ({ login, examExerciseGroupCreation, examAPIRequests }) => {
            await login(admin);
            for (let i = 0; i < exam.numberOfExercisesInExam!; i++) {
                await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.TEXT);
            }
            await examAPIRequests.registerAllCourseStudentsForExam(exam);
        });

        test('Instructor generates individual exams, prepares exercises for start and corresponding checks are marked', async ({
            page,
            login,
            examDetails,
            studentExamManagement,
        }) => {
            await login(instructor);
            await navigateToExamDetailsPage(page, course, exam);
            await examDetails.checkItemUnchecked(ExamChecklistItem.ALL_EXAMS_GENERATED);
            await examDetails.checkItemUnchecked(ExamChecklistItem.ALL_EXERCISES_PREPARED);
            await examDetails.clickStudentExamsToGenerate();
            await studentExamManagement.clickGenerateStudentExams();
            await navigateToExamDetailsPage(page, course, exam);
            await examDetails.checkItemChecked(ExamChecklistItem.ALL_EXAMS_GENERATED);
            await examDetails.checkItemUnchecked(ExamChecklistItem.ALL_EXERCISES_PREPARED);
            await examDetails.clickStudentExamsToPrepareStart();
            await studentExamManagement.clickPrepareExerciseStart();
            await navigateToExamDetailsPage(page, course, exam);
            await examDetails.checkItemChecked(ExamChecklistItem.ALL_EXAMS_GENERATED);
            await examDetails.checkItemChecked(ExamChecklistItem.ALL_EXERCISES_PREPARED);
        });
    });

    test('Instructor sets the publish results and review dates and the corresponding checks are marked', { tag: '@fast' }, async ({ page, login, examDetails, examCreation }) => {
        const exam = await createExam(course, page);
        await login(instructor);
        await navigateToExamDetailsPage(page, course, exam);
        await examDetails.checkItemUnchecked(ExamChecklistItem.PUBLISHING_DATE_SET);
        await examDetails.checkItemUnchecked(ExamChecklistItem.START_DATE_REVIEW_SET);
        await examDetails.checkItemUnchecked(ExamChecklistItem.END_DATE_REVIEW_SET);
        await examDetails.clickEditExamForPublishDate();
        const examEndDate = dayjs(exam.endDate! as dayjs.Dayjs);
        await examCreation.setPublishResultsDate(examEndDate.add(1, 'hour'));
        await examCreation.update();
        await page.waitForURL(`**/exams/${exam.id}`);
        await examDetails.checkItemChecked(ExamChecklistItem.PUBLISHING_DATE_SET);
        await examDetails.checkItemUnchecked(ExamChecklistItem.START_DATE_REVIEW_SET);
        await examDetails.checkItemUnchecked(ExamChecklistItem.END_DATE_REVIEW_SET);
        await examDetails.clickEditExamForReviewDate();
        await examCreation.setStudentReviewStartDate(examEndDate.add(2, 'hour'));
        await examCreation.setStudentReviewEndDate(examEndDate.add(1, 'day'));
        await examCreation.update();
        await page.waitForURL(`**/exams/${exam.id}`);
        await examDetails.checkItemChecked(ExamChecklistItem.PUBLISHING_DATE_SET);
        await examDetails.checkItemChecked(ExamChecklistItem.START_DATE_REVIEW_SET);
        await examDetails.checkItemChecked(ExamChecklistItem.END_DATE_REVIEW_SET);
    });

    test(
        'Student makes a submission and missing assessment check is marked for instructor after assessment',
        { tag: '@slow' },
        async ({ page, login, examDetails, examManagement, courseAssessment, exerciseAssessment, textExerciseAssessment, examAPIRequests }) => {
            const exam = await prepareExam(course, dayjs().add(1, 'day'), ExerciseType.TEXT, page);
            await login(instructor);
            await examAPIRequests.finishExam(exam);
            await navigateToExamDetailsPage(page, course, exam);
            await examDetails.checkItemUnchecked(ExamChecklistItem.UNFINISHED_ASSESSMENTS);
            await startAssessing(course.id!, exam.id!, 60000, examManagement, courseAssessment, exerciseAssessment);
            await textExerciseAssessment.addNewFeedback(5, 'OK');
            await textExerciseAssessment.submit();
            await navigateToExamDetailsPage(page, course, exam);
            await examDetails.checkItemChecked(ExamChecklistItem.UNFINISHED_ASSESSMENTS);
        },
    );

    // This test is skipped for now because it currently fails due to a known issue:
    // https://github.com/ls1intum/Artemis/issues/10074
    test.skip(
        'Student makes a quiz submission and unassessed quizzes check is marked for instructor after assessment',
        { tag: '@slow' },
        async ({ page, login, examDetails, examAPIRequests }) => {
            const exam = await prepareExam(course, dayjs().add(1, 'day'), ExerciseType.QUIZ, page);
            await login(instructor);
            await examAPIRequests.finishExam(exam);
            await navigateToExamDetailsPage(page, course, exam);
            await examDetails.checkItemUnchecked(ExamChecklistItem.UNASSESSED_QUIZZES);
            await examDetails.clickEvaluateQuizExercises();
            await examDetails.checkItemChecked(ExamChecklistItem.UNASSESSED_QUIZZES);
        },
    );

    // This test is skipped for now because it currently fails due to a known issue:
    // https://github.com/ls1intum/Artemis/issues/10076
    test.skip(
        'Student does not submit the exam on time and corresponding check is marked',
        { tag: '@slow' },
        async ({ page, login, examDetails, examAPIRequests, examExerciseGroupCreation, examParticipation }) => {
            const examConfig = {
                startDate: dayjs(),
                endDate: dayjs().add(1, 'day'),
                publishResultsDate: dayjs().add(2, 'day'),
            };
            const exam = await createExam(course, page, examConfig);
            for (let i = 0; i < exam.numberOfExercisesInExam!; i++) {
                await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.TEXT, { textFixture: 'loremIpsum-short.txt' });
            }
            await examAPIRequests.registerStudentForExam(exam, studentOne);
            await examAPIRequests.generateMissingIndividualExams(exam);
            await examAPIRequests.prepareExerciseStartForExam(exam);
            await examParticipation.startParticipation(studentOne, course, exam);
            await login(instructor);
            await examAPIRequests.finishExam(exam);
            await navigateToExamDetailsPage(page, course, exam);
            await examDetails.checkItemUnchecked(ExamChecklistItem.UNSUBMITTED_EXERCISES);
            await examDetails.clickAssessUnsubmittedParticipations();
            await examDetails.checkItemChecked(ExamChecklistItem.UNSUBMITTED_EXERCISES);
        },
    );

    test.afterEach('Delete course', async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });
});

async function createExam(course: Course, page: Page, customConfig?: any) {
    const NUMBER_OF_EXERCISES = 2;
    const EXAM_MAX_POINTS = NUMBER_OF_EXERCISES * 10;

    await Commands.login(page, admin);
    const examConfig = Object.assign({}, customConfig, { course, examMaxPoints: EXAM_MAX_POINTS, numberOfExercisesInExam: NUMBER_OF_EXERCISES });

    const examAPIRequests = new ExamAPIRequests(page);
    return await examAPIRequests.createExam(examConfig);
}

async function navigateToExamDetailsPage(page: Page, course: Course, exam: Exam) {
    await page.goto(`/course-management/${course.id}/exams/${exam.id}`);
}

async function addExamExerciseGroup(examExerciseGroups: ExamExerciseGroupsPage, examExerciseGroupCreation: ExamExerciseGroupCreationPage, isMandatory?: boolean) {
    await examExerciseGroups.clickAddExerciseGroup();
    await examExerciseGroupCreation.typeTitle(`Group ${generateUUID()}`);
    if (isMandatory !== undefined) {
        await examExerciseGroupCreation.setMandatoryBox(isMandatory);
    }
    await examExerciseGroupCreation.clickSave();
}
