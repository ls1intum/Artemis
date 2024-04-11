import { Course } from 'app/entities/course.model';
import dayjs from 'dayjs';
import { Exam } from 'app/entities/exam.model';
import { dayjsToString, generateUUID, titleLowercase } from '../utils';
import examTemplate from '../../fixtures/exam/template.json';
import { Page } from '@playwright/test';
import { COURSE_BASE } from '../constants';
import { Exercise } from 'app/entities/exercise.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { UserCredentials } from '../users';

/**
 * A class which encapsulates all API requests related to exams.
 */
export class ExamAPIRequests {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

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
     * @returns Promise<Exam> representing the exam create.
     */
    async createExam(options: {
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
    }): Promise<Exam> {
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

        const response = await this.page.request.post(`${COURSE_BASE}/${exam.course!.id}/exams`, { data: exam });
        return response.json();
    }

    /**
     * Deletes the exam with the given parameters
     * @param exam the exam object
     * */
    async deleteExam(exam: Exam) {
        await this.page.request.delete(`${COURSE_BASE}/${exam.course!.id}/exams/${exam.id}`);
    }

    /**
     * Register the student for the exam
     * @param exam the exam object
     */
    async registerStudentForExam(exam: Exam, student: UserCredentials) {
        await this.page.request.post(`${COURSE_BASE}/${exam.course!.id}/exams/${exam.id}/students/${student.username}`);
    }

    /**
     * Creates an exam test run with the provided settings.
     * @param exam the exam object
     * @param exerciseArray an array of exercises
     * @param workingTime the working time in seconds
     */
    async createExamTestRun(exam: Exam, exerciseArray: Array<Exercise>, workingTime = 1080) {
        const courseId = exam.course!.id;
        const examId = exam.id!;
        const data = {
            exam,
            exerciseArray,
            workingTime,
        };
        await this.page.request.post(`${COURSE_BASE}/${courseId}/exams/${examId}/test-run`, { data });
    }

    /**
     * Adds an exercise group for the exam
     * @param exam the exam to which the group is added
     * @param title the title of the group
     * @param mandatory if the exercise group is mandatory
     * @returns Promise<ExerciseGroup> representing the exercise group added for the exam.
     * */
    async addExerciseGroupForExam(exam: Exam, title = 'Group ' + generateUUID(), mandatory = true): Promise<ExerciseGroup> {
        const exerciseGroup = new ExerciseGroup();
        exerciseGroup.exam = exam;
        exerciseGroup.title = title;
        exerciseGroup.isMandatory = mandatory;
        const response = await this.page.request.post(`${COURSE_BASE}/${exam.course!.id}/exams/${exam.id}/exerciseGroups`, { data: exerciseGroup });
        return response.json();
    }

    async deleteExerciseGroupForExam(exam: Exam, exerciseGroup: ExerciseGroup) {
        await this.page.request.delete(`${COURSE_BASE}/${exam.course!.id}/exams/${exam.id}/exerciseGroups/${exerciseGroup.id}`);
    }

    /**
     * Generate all missing individual exams
     * @param exam the exam for which the missing exams are generated
     */
    async generateMissingIndividualExams(exam: Exam) {
        await this.page.request.post(`${COURSE_BASE}/${exam.course!.id}/exams/${exam.id}/generate-missing-student-exams`);
    }

    /**
     * Prepares individual exercises for exam start
     * @param exam the exam for which the exercises are prepared
     */
    async prepareExerciseStartForExam(exam: Exam) {
        await this.page.request.post(`${COURSE_BASE}/${exam.course!.id}/exams/${exam.id}/student-exams/start-exercises`);
    }

    /**
     * Gets the exam scores
     * @param exam the exam to get the scores for
     */
    async getExamScores(exam: Exam) {
        const response = await this.page.request.get(`${COURSE_BASE}/${exam.course!.id}/exams/${exam.id}/scores`);
        return await response.json();
    }

    /**
     * Sets the exam grading scale
     * @param exam the exam for which the grading scale is set
     * @param gradingScale the grading scale to set
     */
    async setExamGradingScale(exam: Exam, gradingScale: any) {
        const data = {
            exam,
            ...gradingScale,
        };
        await this.page.request.post(`${COURSE_BASE}/${exam.course!.id}/exams/${exam.id}/grading-scale`, { data });
    }
}
