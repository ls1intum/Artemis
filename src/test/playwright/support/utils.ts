import dayjs from 'dayjs';
import utc from 'dayjs/plugin/utc';
import { v4 as uuidv4 } from 'uuid';
import { Exercise, ExerciseType, ProgrammingExerciseAssessmentType, TIME_FORMAT } from './constants';
import * as fs from 'fs';
import { dirname } from 'path';
import { Browser, Locator, Page, expect } from '@playwright/test';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamAPIRequests } from './requests/ExamAPIRequests';
import { ExerciseAPIRequests } from './requests/ExerciseAPIRequests';
import { ExamExerciseGroupCreationPage } from './pageobjects/exam/ExamExerciseGroupCreationPage';
import { CoursesPage } from './pageobjects/course/CoursesPage';
import { CourseOverviewPage } from './pageobjects/course/CourseOverviewPage';
import { ModelingEditor } from './pageobjects/exercises/modeling/ModelingEditor';
import { OnlineEditorPage } from './pageobjects/exercises/programming/OnlineEditorPage';
import { MultipleChoiceQuiz } from './pageobjects/exercises/quiz/MultipleChoiceQuiz';
import { TextEditorPage } from './pageobjects/exercises/text/TextEditorPage';
import { ExamNavigationBar } from './pageobjects/exam/ExamNavigationBar';
import { ExamStartEndPage } from './pageobjects/exam/ExamStartEndPage';
import { ExamParticipationPage } from './pageobjects/exam/ExamParticipationPage';
import { Commands } from './commands';
import { admin, studentOne } from './users';
import javaPartiallySuccessful from '../fixtures/exercise/programming/java/partially_successful/submission.json';
import { ExamManagementPage } from './pageobjects/exam/ExamManagementPage';
import { CourseAssessmentDashboardPage } from './pageobjects/assessment/CourseAssessmentDashboardPage';
import { ExerciseAssessmentDashboardPage } from './pageobjects/assessment/ExerciseAssessmentDashboardPage';

// Add utc plugin to use the utc timezone
dayjs.extend(utc);

/*
 * This file contains all the global utility functions.
 */

/**
 * Generates a unique identifier.
 */
export function generateUUID() {
    const uuid = uuidv4().replace(/-/g, '');
    return uuid.substr(0, 9);
}

/**
 * Allows to enter date into the UI
 */
export async function enterDate(page: Page, selector: string, date: dayjs.Dayjs) {
    const dateInputField = page.locator(selector).locator('#date-input-field');
    await expect(dateInputField).toBeEnabled();
    await dateInputField.fill(dayjsToString(date), { force: true });
}

/**
 * Formats the day object with the time format which the server uses. Also makes sure that day uses the utc timezone.
 * @param day the day object
 * @returns a formatted string representing the date with utc timezone
 */
export function dayjsToString(day: dayjs.Dayjs) {
    // We need to add the Z at the end. Otherwise, the server can't parse it.
    return day.utc().format(TIME_FORMAT) + 'Z';
}

/**
 * This function is necessary to make the server and the client date comparable.
 * The server sometimes has 3 digit on the milliseconds and sometimes only 1 digit.
 * With this function we always cut the date string after the first digit.
 * @param date the date as a string
 * @returns a date string with only one digit for the milliseconds
 */
export function trimDate(date: string) {
    return date.slice(0, 19);
}

/**
 * Converts a snake_case word to Title Case (each word's first letter capitalized and spaces in between).
 * @param str - The snake_case word to be converted to Title Case.
 * @returns The word in Title Case.
 */
export function titleCaseWord(str: string) {
    str = str.replace('_', ' ');
    const sentence = str.toLowerCase().split(' ');
    for (let i = 0; i < sentence.length; i++) {
        sentence[i] = sentence[i][0].toUpperCase() + sentence[i].slice(1);
    }
    return sentence.join(' ');
}

/**
 * Retrieves the DOM element representing the exercise with the specified ID.
 * @param page - Playwright Page instance used during the test.
 * @param exerciseId - The ID of the exercise for which to retrieve the DOM element.
 * @returns Locator that yields the DOM element representing the exercise.
 */
export function getExercise(page: Page, exerciseId: number) {
    return page.locator(`#exercise-${exerciseId}`);
}

/**
 * Converts a title to lowercase and replaces spaces with hyphens.
 * @param title - The title to be converted to lowercase with hyphens.
 * @returns The converted title in lowercase with hyphens.
 */
export function titleLowercase(title: string) {
    return title.replace(' ', '-').toLowerCase();
}

/**
 * Converts a boolean value to its related icon class.
 * @param boolean - The boolean value to be converted.
 * @returns The corresponding icon class
 */
export function convertBooleanToCheckIconClass(boolean: boolean) {
    const sectionInvalidIcon = '.fa-xmark';
    const sectionValidIcon = '.fa-circle-check';
    return boolean ? sectionValidIcon : sectionInvalidIcon;
}

/**
 * Convert a base64-encoded string to a `Blob`.
 *
 * This is an adaptation of the `base64StringToBlob` function from `blob-util` library.
 * Since Playwright has no access to DOM APIs, we cannot use the one in `blob-util` library as it uses `window` object.
 *
 * Example:
 *
 * ```js
 * var blob = blobUtil.base64StringToBlob(base64String);
 * ```
 * @param base64 - base64-encoded string
 * @param type - the content type (optional)
 * @returns Blob
 */
export function base64StringToBlob(base64: string, type?: string): Blob {
    const buffer = Buffer.from(base64!, 'base64');
    return new Blob([buffer], { type });
}

export async function clearTextField(textField: Locator) {
    await textField.click({ clickCount: 4, force: true });
    await textField.press('Backspace');
}

export async function hasAttributeWithValue(page: Page, selector: string, value: string): Promise<boolean> {
    return page.evaluate(
        ({ selector, value }) => {
            const element = document.querySelector(selector);
            if (!element) return false;
            for (const attr of element.attributes) {
                if (attr.value === value) {
                    return true;
                }
            }
            return false;
        },
        { selector, value },
    );
}

export function parseNumber(text?: string): number | undefined {
    return text ? parseInt(text) : undefined;
}

export async function createFileWithContent(filePath: string, content: string) {
    const directory = dirname(filePath);

    if (!fs.existsSync(directory)) {
        fs.mkdirSync(directory, { recursive: true });
    }
    fs.writeFileSync(filePath, content);
}

export async function newBrowserPage(browser: Browser) {
    const context = await browser.newContext();
    return await context.newPage();
}

/**
 * Drags an element to a droppable element.
 * @param page - Playwright Page instance used during the test.
 * @param draggable - Locator of the element to be dragged.
 * @param droppable - Locator of the element to be dropped on.
 */
export async function drag(page: Page, draggable: Locator, droppable: Locator) {
    const box = (await droppable.boundingBox())!;
    // By hovering over the droppable element, we ensure it's not hidden by any other element.
    await droppable.hover();
    await draggable.hover();

    await page.mouse.down();
    await droppable.scrollIntoViewIfNeeded();
    // we have to move to the left instead of the right, because otherwise the element is outside the box as the x coordinate of the bounding box seems a bit off
    await page.mouse.move(box.x - box.width / 2, box.y + box.height / 2, {
        steps: 5,
    });

    await page.mouse.up();
}

/*
 * Exam utility functions
 */

export async function prepareExam(course: Course, end: dayjs.Dayjs, exerciseType: ExerciseType, page: Page, numberOfCorrectionRounds: number = 1): Promise<Exam> {
    const examAPIRequests = new ExamAPIRequests(page);
    const exerciseAPIRequests = new ExerciseAPIRequests(page);
    const examExerciseGroupCreation = new ExamExerciseGroupCreationPage(page, examAPIRequests, exerciseAPIRequests);
    const courseList = new CoursesPage(page);
    const courseOverview = new CourseOverviewPage(page);
    const modelingExerciseEditor = new ModelingEditor(page);
    const programmingExerciseEditor = new OnlineEditorPage(page);
    const quizExerciseMultipleChoice = new MultipleChoiceQuiz(page);
    const textExerciseEditor = new TextEditorPage(page);
    const examNavigation = new ExamNavigationBar(page);
    const examStartEnd = new ExamStartEndPage(page);
    const examParticipation = new ExamParticipationPage(
        courseList,
        courseOverview,
        examNavigation,
        examStartEnd,
        modelingExerciseEditor,
        programmingExerciseEditor,
        quizExerciseMultipleChoice,
        textExerciseEditor,
        page,
    );

    await Commands.login(page, admin);
    const resultDate = end.add(1, 'second');
    const examConfig = {
        course,
        startDate: dayjs(),
        endDate: end,
        numberOfCorrectionRoundsInExam: numberOfCorrectionRounds,
        examStudentReviewStart: resultDate,
        examStudentReviewEnd: resultDate.add(1, 'minute'),
        publishResultsDate: resultDate,
        gracePeriod: 10,
    };
    const exam = await examAPIRequests.createExam(examConfig);
    let additionalData = {};
    switch (exerciseType) {
        case ExerciseType.PROGRAMMING:
            additionalData = {
                submission: javaPartiallySuccessful,
                progExerciseAssessmentType: ProgrammingExerciseAssessmentType.SEMI_AUTOMATIC,
            };
            break;
        case ExerciseType.TEXT:
            additionalData = { textFixture: 'loremIpsum-short.txt' };
            break;
        case ExerciseType.QUIZ:
            additionalData = { quizExerciseID: 0 };
            break;
    }

    const exercise = await examExerciseGroupCreation.addGroupWithExercise(exam, exerciseType, additionalData);
    await examAPIRequests.registerStudentForExam(exam, studentOne);
    await examAPIRequests.generateMissingIndividualExams(exam);
    await examAPIRequests.prepareExerciseStartForExam(exam);
    exercise.additionalData = additionalData;
    await makeExamSubmission(course, exam, exercise, page, examParticipation, examNavigation, examStartEnd);
    return exam;
}

export async function makeExamSubmission(
    course: Course,
    exam: Exam,
    exercise: Exercise,
    page: Page,
    examParticipation: ExamParticipationPage,
    examNavigation: ExamNavigationBar,
    examStartEnd: ExamStartEndPage,
) {
    await examParticipation.startParticipation(studentOne, course, exam);
    await examNavigation.openOrSaveExerciseByTitle(exercise.exerciseGroup!.title!);
    await examParticipation.makeSubmission(exercise.id!, exercise.type!, exercise.additionalData);
    await page.waitForTimeout(2000);
    await examNavigation.handInEarly();
    await examStartEnd.finishExam();
}

/**
 * Waits for the exam to end if it hasn't already.
 * This is necessary because the assessment dashboard button only appears after the exam ends.
 * @param examEnd - The exam end date
 * @param page - The Playwright page object (used for waitForTimeout)
 */
export async function waitForExamEnd(examEnd: dayjs.Dayjs, page: Page) {
    if (examEnd.isAfter(dayjs())) {
        const timeToWait = examEnd.diff(dayjs()) + 1000; // Add 1 second buffer
        console.log(`Waiting ${timeToWait}ms for exam to end...`);
        await page.waitForTimeout(timeToWait);
    }
}

export async function startAssessing(
    courseID: number,
    examID: number,
    timeout: number,
    examManagement: ExamManagementPage,
    courseAssessment: CourseAssessmentDashboardPage,
    exerciseAssessment: ExerciseAssessmentDashboardPage,
    toggleSecondRound: boolean = false,
    isFirstTimeAssessing: boolean = true,
) {
    await examManagement.openAssessmentDashboard(courseID, examID, timeout);
    await courseAssessment.clickExerciseDashboardButton();
    if (toggleSecondRound) {
        await exerciseAssessment.toggleSecondCorrectionRound();
    }
    if (isFirstTimeAssessing) {
        await exerciseAssessment.clickHaveReadInstructionsButton();
    }
    await exerciseAssessment.clickStartNewAssessment();
    exerciseAssessment.getLockedMessage();
}
