import { Course } from 'app/course/shared/entities/course.model';
import dayjs from 'dayjs';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { dayjsToString, generateUUID, titleLowercase } from '../utils';
import examTemplate from '../../fixtures/exam/template.json';
import { APIResponse, Page, expect } from '@playwright/test';
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
     * Fails fast with the status and body when an API call did not succeed. Setup calls used to ignore the response, so
     * a rejected request only surfaced later as an unrelated UI failure that looked like a flaky test.
     */
    private async expectOk(response: APIResponse, action: string): Promise<APIResponse> {
        if (!response.ok()) {
            throw new Error(`Failed to ${action}: ${response.status()} ${await response.text()}`);
        }
        return response;
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
     *   - workingTime: The allowed working time for the exam in seconds (optional, default: the exam duration for a real exam, 86400 seconds or 1 day for a test exam).
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
            workingTime: requestedWorkingTime,
            examStudentReviewStart = null,
            examStudentReviewEnd = null,
            publishResultsDate = null,
            examSummaryPublicationDate = null,
            gracePeriod = 30,
        } = options;

        // A real exam's working time is its duration: the server derives every student's working time from the dates and
        // rescales the exam's own working time by the same delta when the end changes, so a stale value (the old fixed
        // one day) would make shortening the exam fail once the change exceeds it. A test exam's working time is per attempt.
        const workingTime = requestedWorkingTime ?? (testExam ? 86400 : dayjs(endDate as any).diff(dayjs(startDate as any), 'seconds'));

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
     * Gets an exam together with the current time of the server, taken from the `Date` header of that very response.
     * Deadlines are set and awaited on the server clock, so a skewed clock of the test runner cannot make a test race the exam.
     */
    private async getExamWithServerTime(exam: Exam): Promise<{ current: Exam; serverNow: dayjs.Dayjs }> {
        const response = await this.expectOk(await this.page.request.get(`api/exam/courses/${exam.course!.id}/exams/${exam.id}`), `get exam ${exam.id}`);
        return { current: (await response.json()) as Exam, serverNow: dayjs(response.headers()['date']) };
    }

    /**
     * Updates an exam: takes its current state from the server, applies the given changes and saves the result.
     * The server rescales the students' working times when the duration changes and reschedules programming exercises when dates move.
     * @param exam the exam to update
     * @param changes the properties to overwrite, e.g. dates as dayjs values
     */
    async updateExam(exam: Exam, changes: Record<string, unknown>): Promise<Exam> {
        const { current } = await this.getExamWithServerTime(exam);
        const response = await this.expectOk(
            await this.page.request.put(`api/exam/courses/${exam.course!.id}/exams`, { data: { ...current, ...changes } }),
            `update exam ${exam.id}`,
        );
        return (await response.json()) as Exam;
    }

    /**
     * Waits until the exam counts as over on the server, including its grace period: from then on the last student can no longer
     * hand in, and assessment may start. Polls the server clock instead of sleeping for a computed time.
     */
    async waitUntilExamIsOver(exam: Exam) {
        const gracePeriodInSeconds = exam.gracePeriod ?? 0;
        await expect
            .poll(
                async () => {
                    const { current, serverNow } = await this.getExamWithServerTime(exam);
                    // The Date header has a resolution of one second, so require a full second of margin.
                    return serverNow.isAfter(dayjs(current.endDate as any).add((current.gracePeriod ?? gracePeriodInSeconds) + 1, 'seconds'));
                },
                { message: `exam ${exam.id} should be over including its grace period`, intervals: [1000], timeout: (gracePeriodInSeconds + 120) * 1000 },
            )
            .toBe(true);
    }

    /**
     * Ends an exam that a student has already handed in and makes it ready for assessment: the exam ends a moment from now,
     * its results are published right after the end, and the student review period opens for five minutes.
     * Returns once the exam is over including its grace period.
     * <p>
     * Setting the deadline only after the participation lets the participation take as long as it needs; a deadline fixed
     * at creation time had to be guessed, and a slow or loaded run would either cut the participation short or wait needlessly.
     *
     * @param exam the exam to conclude
     * @param options `publishResults: false` leaves the results unpublished and the review period unset
     * @returns the exam with its new dates
     */
    async concludeExam(exam: Exam, options: { publishResults?: boolean } = {}): Promise<Exam> {
        const { publishResults = true } = options;
        const { current, serverNow } = await this.getExamWithServerTime(exam);
        const end = serverNow.add(2, 'seconds');
        const resultDate = end.add(1, 'second');
        const concluded = await this.updateExam(exam, {
            endDate: dayjsToString(end),
            workingTime: current.testExam ? current.workingTime : end.diff(dayjs(current.startDate as any), 'seconds'),
            publishResultsDate: publishResults ? dayjsToString(resultDate) : null,
            examStudentReviewStart: publishResults ? dayjsToString(resultDate) : null,
            examStudentReviewEnd: publishResults ? dayjsToString(resultDate.add(5, 'minutes')) : null,
        });
        await this.waitUntilExamIsOver(concluded);
        return concluded;
    }

    /**
     * Determines the time left until the exam ends and finishes the exam by subtracting it from the working time.
     */
    async finishExam(exam: Exam) {
        const examEndDate = dayjs(exam.endDate! as dayjs.Dayjs);
        // Determine the time left until the exam ends and add extra minute
        // to make sure the exam is finished after subtracting it from the working time
        const examTimeLeftInSeconds = examEndDate.diff(dayjs(), 'seconds') + 60;
        if (examTimeLeftInSeconds > 0) {
            await this.page.request.patch(`api/exam/courses/${exam.course!.id}/exams/${exam.id}/working-time`, { data: -examTimeLeftInSeconds });
        }
    }
}
