import { Course } from 'app/course/shared/entities/course.model';
import dayjs from 'dayjs';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { dayjsToString, generateUUID, titleLowercase } from '../utils';
import examTemplate from '../../fixtures/exam/template.json';
import { Page } from '@playwright/test';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { UserCredentials } from '../users';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';

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
        examSummaryPublicationDate?: dayjs.Dayjs;
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
            examSummaryPublicationDate = null,
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
            examSummaryPublicationDate,
            gracePeriod,
            channelName: titleLowercase(title),
        } as Exam;

        if (testExam) {
            exam.numberOfCorrectionRoundsInExam = 0;
        }

        const response = await this.page.request.post(`api/exam/courses/${exam.course!.id}/exams`, { data: exam });
        return response.json();
    }

    /**
     * Deletes the exam with the given parameters
     * @param exam the exam object
     * */
    async deleteExam(exam: Exam) {
        // beforeAll failures may leave exam partially initialized; teardown should stay best-effort.
        if (!exam?.id || !exam.course?.id) {
            return;
        }
        await this.page.request.delete(`api/exam/courses/${exam.course!.id}/exams/${exam.id}`);
    }

    /**
     * Register the student for the exam.
     * Uses the bulk students endpoint (POST .../students with a list of ExamUserDTOs); the server resolves each
     * entry via UserService.findUser, which matches by login when a login is provided. The former
     * POST .../students/{login} endpoint was removed during the exam-registration refactor.
     */
    async registerStudentForExam(exam: Exam, student: UserCredentials) {
        await this.page.request.post(`api/exam/courses/${exam.course!.id}/exams/${exam.id}/students`, {
            data: [{ login: student.username }],
        });
    }

    /**
     * Register all course students for the exam
     */
    async registerAllCourseStudentsForExam(exam: Exam) {
        await this.page.request.post(`api/exam/courses/${exam.course!.id}/exams/${exam.id}/register-course-students`);
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
        const response = await this.page.request.post(`api/exam/courses/${exam.course!.id}/exams/${exam.id}/exercise-groups`, { data: exerciseGroup });
        return response.json();
    }

    async deleteExerciseGroupForExam(exam: Exam, exerciseGroup: ExerciseGroup) {
        await this.page.request.delete(`api/exam/courses/${exam.course!.id}/exams/${exam.id}/exercise-groups/${exerciseGroup.id}`);
    }

    /**
     * Generate all missing individual exams
     * @param exam the exam for which the missing exams are generated
     */
    async generateMissingIndividualExams(exam: Exam) {
        const response = await this.page.request.post(`api/exam/courses/${exam.course!.id}/exams/${exam.id}/generate-missing-student-exams`);
        return await response.json();
    }

    /**
     * Get all student-exams of an exam
     * @param exam the exam for which the student-exams are fetched
     */
    async getAllStudentExams(exam: Exam) {
        const response = await this.page.request.get(`api/exam/courses/${exam.course!.id}/exams/${exam.id}/student-exams`);
        return await response.json();
    }

    /**
     * Gets the exercise groups (including their exercises) of an exam.
     * @param exam the exam to get the exercise groups for
     */
    async getExerciseGroups(exam: Exam): Promise<ExerciseGroup[]> {
        const response = await this.page.request.get(`api/exam/courses/${exam.course!.id}/exams/${exam.id}/exercise-groups`);
        if (!response.ok()) {
            throw new Error(`Failed to get exercise groups for exam ${exam.id}: ${response.status()}`);
        }
        return (await response.json()) as ExerciseGroup[];
    }

    /**
     * Prepares individual exercises for exam start
     * @param exam the exam for which the exercises are prepared
     */
    async prepareExerciseStartForExam(exam: Exam) {
        await this.page.request.post(`api/exam/courses/${exam.course!.id}/exams/${exam.id}/student-exams/start-exercises`);
    }

    /**
     * Gets the exam scores
     * @param exam the exam to get the scores for
     */
    async getExamScores(exam: Exam) {
        const response = await this.page.request.get(`api/exam/courses/${exam.course!.id}/exams/${exam.id}/scores`);
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
        await this.page.request.post(`api/assessment/courses/${exam.course!.id}/exams/${exam.id}/grading-scale`, { data });
    }

    async getGradeSummary(exam: Exam, studentExam: StudentExam) {
        const response = await this.page.request.get(`api/exam/courses/${exam.course!.id}/exams/${exam.id}/student-exams/${studentExam.id}/grade-summary`);
        return await response.json();
    }

    /**
     * Ends the exam by shrinking its working time to the time already elapsed, then waits until the exam counts as over
     * (start date plus working time plus grace period lies in the past). The server rejects a working time or duration
     * that is not positive, so an exam that has barely started keeps one second and the wait covers the rest.
     */
    async finishExam(exam: Exam) {
        const startDate = dayjs(exam.startDate! as dayjs.Dayjs);
        const endDate = dayjs(exam.endDate! as dayjs.Dayjs);
        const gracePeriodInSeconds = exam.gracePeriod ?? 0;
        const newDurationInSeconds = Math.max(dayjs().diff(startDate, 'seconds') - gracePeriodInSeconds - 1, 1);
        const workingTimeChangeInSeconds = newDurationInSeconds - endDate.diff(startDate, 'seconds');
        if (workingTimeChangeInSeconds < 0) {
            const response = await this.page.request.patch(`api/exam/courses/${exam.course!.id}/exams/${exam.id}/working-time`, { data: workingTimeChangeInSeconds });
            if (!response.ok()) {
                throw new Error(`Failed to finish exam ${exam.id}: ${response.status()} ${await response.text()}`);
            }
        }
        const millisecondsUntilOver = startDate.add(newDurationInSeconds + gracePeriodInSeconds + 1, 'seconds').diff(dayjs());
        if (millisecondsUntilOver > 0) {
            await this.page.waitForTimeout(millisecondsUntilOver);
        }
    }
}
