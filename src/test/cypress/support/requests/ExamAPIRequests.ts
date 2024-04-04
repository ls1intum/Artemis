import examTemplate from '../../fixtures/exam/template.json';
import { COURSE_BASE, DELETE, Exercise, POST } from '../constants';
import { CypressCredentials } from '../users';
import { dayjsToString, generateUUID, titleLowercase } from '../utils';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import dayjs from 'dayjs/esm';

/**
 * A class which encapsulates all API requests related to exams.
 */
export class ExamAPIRequests {
    /**
     * Creates an exam for the specified course with various options.
     * @param options - An object containing the options for creating the exam.
     *   - course: The course to which the exam belongs (optional, default: undefined).
     *   - title: The title of the exam (optional, default: auto-generated).
     *   - testExam: Set to true to create a test exam (optional, default: false).
     *   - visibleDate: The date when the exam becomes visible (optional, default: current date).
     *   - startDate: The start date of the exam (optional, default: current date + 1 day).
     *   - endDate: The end date of the exam (optional, default: current date + 2 days).
     *   - examMaxPoints: The maximum points achievable in the exam (optional, default: undefined).
     *   - numberOfExercisesInExam: The number of exercises in the exam (optional, default: undefined).
     *   - numberOfCorrectionRoundsInExam: The number of correction rounds for the exam (optional, default: undefined).
     *   - workingTime: The allowed working time for the exam in seconds (optional, default: 86400 seconds or 1 day).
     *   - examStudentReviewStart: The date when students can start reviewing their exam (optional, default: undefined).
     *   - examStudentReviewEnd: The date when students can no longer review their exam (optional, default: undefined).
     *   - publishResultsDate: The date when exam results will be published (optional, default: undefined).
     *   - gracePeriod: The grace period in seconds for late submissions (optional, default: undefined).
     *   - channelName: The channel name for the exam (optional, default: auto-generated based on title).
     * @returns A Cypress.Chainable<Cypress.Response<Exam>> representing the API request response.
     */
    createExam(options: {
        course?: Course;
        title?: string;
        testExam?: boolean;
        visibleDate?: dayjs.Dayjs;
        startDate?: dayjs.Dayjs;
        endDate?: dayjs.Dayjs;
        examMaxPoints?: number;
        numberOfExercisesInExam?: number;
        numberOfCorrectionRoundsInExam?: number;
        workingTime?: number;
        examStudentReviewStart?: dayjs.Dayjs;
        examStudentReviewEnd?: dayjs.Dayjs;
        publishResultsDate?: dayjs.Dayjs;
        gracePeriod?: number;
        channelName?: string;
    }): Cypress.Chainable<Cypress.Response<Exam>> {
        const tempTitle = 'exam' + generateUUID();

        const {
            course,
            title = tempTitle,
            testExam = false,
            visibleDate = dayjsToString(dayjs().subtract(1, 'day')),
            startDate = dayjsToString(dayjs().add(1, 'day')),
            endDate = dayjsToString(dayjs().add(2, 'day')),
            examMaxPoints = 10,
            numberOfExercisesInExam = 1,
            numberOfCorrectionRoundsInExam = 1,
            workingTime = 86400,
            examStudentReviewStart = null,
            examStudentReviewEnd = null,
            publishResultsDate = null,
            gracePeriod = 30,
        } = options;

        const exam = {
            ...examTemplate,
            course,
            title,
            testExam,
            visibleDate,
            startDate,
            endDate,
            examMaxPoints,
            numberOfExercisesInExam,
            numberOfCorrectionRoundsInExam,
            workingTime,
            examStudentReviewStart,
            examStudentReviewEnd,
            publishResultsDate,
            gracePeriod,
            channelName: titleLowercase(title),
        } as Exam;

        if (testExam) {
            exam.numberOfCorrectionRoundsInExam = 0;
        }

        return cy.request({
            url: `${COURSE_BASE}/${exam.course!.id}/exams`,
            method: POST,
            body: exam,
        });
    }

    /**
     * Deletes the exam with the given parameters
     * @param exam the exam object
     * @returns A Cypress.Chainable<Cypress.Response<any>> representing the API request response.
     * */
    deleteExam(exam: Exam) {
        return cy.request({ method: DELETE, url: `${COURSE_BASE}/${exam.course!.id}/exams/${exam.id}` });
    }

    /**
     * Register the student for the exam
     * @param exam the exam object
     * @returns A Cypress.Chainable<Cypress.Response<any>> representing the API request response.
     */
    registerStudentForExam(exam: Exam, student: CypressCredentials) {
        return cy.request({ method: POST, url: `${COURSE_BASE}/${exam.course!.id}/exams/${exam.id}/students/${student.username}` });
    }

    /**
     * Creates an exam with the provided settings.
     * @param exam the exam object
     * @param exerciseArray an array of exercises
     * @param workingTime the working time in seconds
     * @returns A Cypress.Chainable<Cypress.Response<any>> representing the API request response.
     */
    createExamTestRun(exam: Exam, exerciseArray: Array<Exercise>, workingTime = 1080) {
        const courseId = exam.course!.id;
        const examId = exam.id!;
        const body = {
            exam,
            exerciseArray,
            workingTime,
        };
        return cy.request({ url: `${COURSE_BASE}/${courseId}/exams/${examId}/test-run`, method: POST, body });
    }

    /**
     * Add exercise group to exam
     * @param exam the exam to which the group is added
     * @param title the title of the group
     * @param mandatory if the exercise group is mandatory
     * @returns A Cypress.Chainable<Cypress.Response<any>> representing the API request response.
     * */
    addExerciseGroupForExam(exam: Exam, title = 'Group ' + generateUUID(), mandatory = true) {
        const exerciseGroup = new ExerciseGroup();
        exerciseGroup.exam = exam;
        exerciseGroup.title = title;
        exerciseGroup.isMandatory = mandatory;
        return cy.request({ method: POST, url: `${COURSE_BASE}/${exam.course!.id}/exams/${exam.id}/exerciseGroups`, body: exerciseGroup });
    }

    /**
     * Generate all missing individual exams
     * @param exam the exam for which the missing exams are generated
     * @returns A Cypress.Chainable<Cypress.Response<any>> representing the API request response.
     */
    generateMissingIndividualExams(exam: Exam) {
        return cy.request({ method: POST, url: `${COURSE_BASE}/${exam.course!.id}/exams/${exam.id}/generate-missing-student-exams` });
    }

    /**
     * Prepares individual exercises for exam start
     * @param exam the exam for which the exercises are prepared
     * @returns A Cypress.Chainable<Cypress.Response<any>> representing the API request response.
     */
    prepareExerciseStartForExam(exam: Exam) {
        return cy.request({ method: POST, url: `${COURSE_BASE}/${exam.course!.id}/exams/${exam.id}/student-exams/start-exercises` });
    }
}
