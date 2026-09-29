import { Course } from 'app/course/shared/entities/course.model';
import dayjs from 'dayjs';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { annotateRecovery, dayjsToString, generateUUID, titleLowercase } from '../utils';
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

        const response = await this.expectOk(await this.page.request.post(`api/exam/courses/${exam.course!.id}/exams`, { data: exam }), `create exam '${title}'`);
        return response.json();
    }

    /**
     * Creates an exam that is already running: it became visible three minutes ago, started two minutes ago and lasts another hour,
     * with one exercise worth ten points. This is the starting point of the tests in which a student takes the exam; everything can
     * be overridden through the options of {@link createExam}.
     */
    async createRunningExam(options: Parameters<ExamAPIRequests['createExam']>[0] = {}): Promise<Exam> {
        return await this.createExam({
            visibleDate: dayjs().subtract(3, 'minutes'),
            startDate: dayjs().subtract(2, 'minutes'),
            endDate: dayjs().add(1, 'hour'),
            examMaxPoints: 10,
            numberOfExercisesInExam: 1,
            ...options,
        });
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
        // Deleting an exam whose programming exercises still have builds in flight fails with a server error, because the build results
        // are written while the exercise is being removed. Such builds finish within seconds, so the delete is repeated for a bounded time;
        // a retry is recorded in the report and anything that still fails afterwards is a real failure.
        let attempts = 0;
        await expect(async () => {
            const response = await this.page.request.delete(`api/exam/courses/${exam.course!.id}/exams/${exam.id}`);
            // A missing exam is fine (a test may already have deleted it); anything else would leak the exam into later tests.
            if (response.ok() || response.status() === 404) {
                return;
            }
            attempts++;
            if (attempts === 2) {
                annotateRecovery(`deleteExam: exam ${exam.id} could not be deleted at first (${response.status()}), retrying while its builds finish`);
            }
            throw new Error(`Failed to delete exam ${exam.id}: ${response.status()} ${await response.text()}`);
        }).toPass({ intervals: [2000], timeout: 30_000 });
    }

    /**
     * Register the student for the exam.
     * Uses the bulk students endpoint (POST .../students with a list of ExamUserDTOs); the server resolves each
     * entry via UserService.findUser, which matches by login when a login is provided. The former
     * POST .../students/{login} endpoint was removed during the exam-registration refactor.
     */
    async registerStudentForExam(exam: Exam, student: UserCredentials) {
        await this.expectOk(
            await this.page.request.post(`api/exam/courses/${exam.course!.id}/exams/${exam.id}/students`, {
                data: [{ login: student.username }],
            }),
            `register ${student.username} for exam ${exam.id}`,
        );
    }

    /**
     * Register all course students for the exam
     */
    async registerAllCourseStudentsForExam(exam: Exam) {
        await this.expectOk(
            await this.page.request.post(`api/exam/courses/${exam.course!.id}/exams/${exam.id}/register-course-students`),
            `register all course students for exam ${exam.id}`,
        );
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
        const response = await this.expectOk(
            await this.page.request.post(`api/exam/courses/${exam.course!.id}/exams/${exam.id}/exercise-groups`, { data: exerciseGroup }),
            `add exercise group '${exerciseGroup.title}' to exam ${exam.id}`,
        );
        return response.json();
    }

    async deleteExerciseGroupForExam(exam: Exam, exerciseGroup: ExerciseGroup) {
        const response = await this.page.request.delete(`api/exam/courses/${exam.course!.id}/exams/${exam.id}/exercise-groups/${exerciseGroup.id}`);
        // A test may already have deleted the group through the UI.
        if (response.status() !== 404) {
            await this.expectOk(response, `delete exercise group ${exerciseGroup.id} of exam ${exam.id}`);
        }
    }

    /**
     * Generate all missing individual exams
     * @param exam the exam for which the missing exams are generated
     */
    async generateMissingIndividualExams(exam: Exam) {
        const response = await this.expectOk(
            await this.page.request.post(`api/exam/courses/${exam.course!.id}/exams/${exam.id}/generate-missing-student-exams`),
            `generate missing student exams for exam ${exam.id}`,
        );
        return await response.json();
    }

    /**
     * Get all student-exams of an exam
     * @param exam the exam for which the student-exams are fetched
     */
    async getAllStudentExams(exam: Exam) {
        const response = await this.expectOk(
            await this.page.request.get(`api/exam/courses/${exam.course!.id}/exams/${exam.id}/student-exams`),
            `get student exams of exam ${exam.id}`,
        );
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
        await this.expectOk(
            await this.page.request.post(`api/exam/courses/${exam.course!.id}/exams/${exam.id}/student-exams/start-exercises`),
            `prepare exercise start for exam ${exam.id}`,
        );
    }

    /**
     * Gets the exam scores
     * @param exam the exam to get the scores for
     */
    async getExamScores(exam: Exam) {
        const response = await this.expectOk(await this.page.request.get(`api/exam/courses/${exam.course!.id}/exams/${exam.id}/scores`), `get scores of exam ${exam.id}`);
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
        await this.expectOk(
            await this.page.request.post(`api/assessment/courses/${exam.course!.id}/exams/${exam.id}/grading-scale`, { data }),
            `set grading scale of exam ${exam.id}`,
        );
    }

    async getGradeSummary(exam: Exam, studentExam: StudentExam) {
        const response = await this.expectOk(
            await this.page.request.get(`api/exam/courses/${exam.course!.id}/exams/${exam.id}/student-exams/${studentExam.id}/grade-summary`),
            `get grade summary of student exam ${studentExam.id}`,
        );
        return await response.json();
    }

    /**
     * Gets an exam, optionally including its exercise groups.
     * @param exam the exam to fetch
     * @param withExerciseGroups whether the exercise groups (and their exercises) are included
     */
    async getExam(exam: Exam, withExerciseGroups = false): Promise<Exam> {
        const response = await this.expectOk(
            await this.page.request.get(`api/exam/courses/${exam.course!.id}/exams/${exam.id}?withExerciseGroups=${withExerciseGroups}`),
            `get exam ${exam.id}`,
        );
        return (await response.json()) as Exam;
    }

    /**
     * Gets a single student exam.
     * @param exam the exam the student exam belongs to
     * @param studentExamId the id of the student exam
     */
    async getStudentExam(exam: Exam, studentExamId: number): Promise<StudentExam> {
        const response = await this.expectOk(
            await this.page.request.get(`api/exam/courses/${exam.course!.id}/exams/${exam.id}/student-exams/${studentExamId}`),
            `get student exam ${studentExamId}`,
        );
        return (await response.json()) as StudentExam;
    }

    /**
     * Gets the logged-in student's own student exam including its exercises, participations and submissions, exactly as
     * the client loads it when the exam is conducted. The student must already be inside the exam's start window.
     * @param exam the exam the student takes
     */
    async getOwnStudentExamForConduction(exam: Exam): Promise<StudentExam> {
        const base = `api/exam/courses/${exam.course!.id}/exams/${exam.id}`;
        const own = await this.expectOk(await this.page.request.get(`${base}/own-student-exam`), `get own student exam of exam ${exam.id}`);
        const studentExamId = (await own.json()).id;
        const conduction = await this.expectOk(
            await this.page.request.get(`${base}/student-exams/${studentExamId}/conduction`),
            `get conduction data of student exam ${studentExamId}`,
        );
        return (await conduction.json()) as StudentExam;
    }

    /**
     * Gets the summary of the logged-in student's own student exam, with the submissions the server has stored for every exercise.
     * The server only hands it out once the student exam is submitted and the summary is published.
     * @param exam the exam the student took
     */
    async getOwnStudentExamSummary(exam: Exam): Promise<StudentExam> {
        let studentExamId: number;
        if (exam.testExam) {
            // Asking a test exam for "the" student exam of the student would start a new attempt, so pick the latest attempt that was handed in.
            const attempts = await this.getOwnTestExamAttempts(exam);
            const submitted = attempts.filter((attempt) => attempt.submitted);
            if (submitted.length === 0) {
                throw new Error(`The student has not handed in any attempt of test exam ${exam.id}`);
            }
            studentExamId = Math.max(...submitted.map((attempt) => attempt.id!));
        } else {
            studentExamId = await this.getOwnStudentExamId(exam);
        }
        return await this.getStudentExamSummary(exam, studentExamId);
    }

    /**
     * Gets the summary of one student exam of the logged-in student, e.g. one attempt of a test exam.
     */
    async getStudentExamSummary(exam: Exam, studentExamId: number): Promise<StudentExam> {
        const summary = await this.expectOk(
            await this.page.request.get(`api/exam/courses/${exam.course!.id}/exams/${exam.id}/student-exams/${studentExamId}/summary`),
            `get summary of student exam ${studentExamId}`,
        );
        return (await summary.json()) as StudentExam;
    }

    /**
     * Gets all attempts (started or not, handed in or not) the logged-in student has of a test exam.
     */
    async getOwnTestExamAttempts(exam: Exam): Promise<StudentExam[]> {
        const attempts = await this.expectOk(await this.page.request.get(`api/exam/courses/${exam.course!.id}/test-exams-per-user`), `get test exam attempts of the student`);
        return ((await attempts.json()) as StudentExam[]).filter((attempt) => attempt.exam?.id === exam.id);
    }

    /**
     * Gets the id of the participation the logged-in student holds for an exercise of the exam.
     * @param exam the exam the student takes
     * @param exerciseId the exercise inside the exam
     */
    async getOwnParticipationId(exam: Exam, exerciseId: number): Promise<number> {
        const studentExam = await this.getOwnStudentExamForConduction(exam);
        const exercise = (studentExam.exercises ?? []).find((candidate) => candidate.id === exerciseId);
        const participationId = exercise?.studentParticipations?.[0]?.id;
        if (participationId === undefined) {
            throw new Error(`Student exam ${studentExam.id} holds no participation for exercise ${exerciseId}`);
        }
        return participationId;
    }

    /**
     * Changes the working time of all student exams of an exam by the given delta and returns the updated exam.
     * The server broadcasts a working-time live event to every student who is currently taking the exam.
     * @param exam the exam to change
     * @param workingTimeChangeInSeconds the delta in seconds; positive extends, negative shortens, zero is rejected by the server
     */
    async changeExamWorkingTime(exam: Exam, workingTimeChangeInSeconds: number): Promise<Exam> {
        const response = await this.expectOk(
            await this.page.request.patch(`api/exam/courses/${exam.course!.id}/exams/${exam.id}/working-time`, { data: workingTimeChangeInSeconds }),
            `change working time of exam ${exam.id} by ${workingTimeChangeInSeconds}s`,
        );
        return (await response.json()) as Exam;
    }

    /**
     * Sets the working time of a single student exam and returns the updated student exam.
     * @param exam the exam the student exam belongs to
     * @param studentExamId the id of the student exam
     * @param workingTimeInSeconds the new absolute working time of this student in seconds
     */
    async setStudentExamWorkingTime(exam: Exam, studentExamId: number, workingTimeInSeconds: number): Promise<StudentExam> {
        const response = await this.expectOk(
            await this.page.request.patch(`api/exam/courses/${exam.course!.id}/exams/${exam.id}/student-exams/${studentExamId}/working-time`, { data: workingTimeInSeconds }),
            `set working time of student exam ${studentExamId} to ${workingTimeInSeconds}s`,
        );
        return (await response.json()) as StudentExam;
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
     * Shortens (or extends) a running exam so that it ends the given number of seconds from now, measured on the server clock.
     * <p>
     * This is the robust way to test the end of an exam: the deadline is set after the setup is done, so a slow setup
     * cannot eat into it, and the resulting working-time live event also exercises the client's timer update.
     *
     * @param exam the exam to change
     * @param secondsFromNow how long the exam should still run; must be at least 1
     * @returns the point in time (server clock) at which the exam ends, without its grace period
     */
    async endExamIn(exam: Exam, secondsFromNow: number): Promise<dayjs.Dayjs> {
        const { current, serverNow } = await this.getExamWithServerTime(exam);
        const newEnd = serverNow.add(secondsFromNow, 'seconds');
        // The server applies the delta to the end date and to every student's working time, so the delta has to be measured against the end date.
        const change = newEnd.diff(dayjs(current.endDate as any), 'seconds');
        if (change !== 0) {
            await this.changeExamWorkingTime(exam, change);
        }
        return newEnd;
    }

    /**
     * Sets the publication of the results to the given number of seconds from now on the server clock, and opens the student review
     * period (the time in which students may complain) at the same moment for ten minutes.
     * @returns the point in time at which the results are published
     */
    async publishResultsIn(exam: Exam, secondsFromNow: number): Promise<dayjs.Dayjs> {
        const { serverNow } = await this.getExamWithServerTime(exam);
        const publish = serverNow.add(secondsFromNow, 'seconds');
        await this.updateExam(exam, {
            publishResultsDate: dayjsToString(publish),
            examStudentReviewStart: dayjsToString(publish),
            examStudentReviewEnd: dayjsToString(publish.add(10, 'minutes')),
        });
        return publish;
    }

    /**
     * Closes the student review period a moment from now and waits until it is closed on the server clock.
     */
    async closeReviewPeriod(exam: Exam) {
        const { serverNow } = await this.getExamWithServerTime(exam);
        const reviewEnd = serverNow.add(1, 'second');
        await this.updateExam(exam, { examStudentReviewEnd: dayjsToString(reviewEnd) });
        await this.waitUntilServerClockIsAfter(exam, reviewEnd);
    }

    /**
     * Gets the id of the own student exam of the student who is logged in on another page, for a test that acts as somebody else on its own page.
     */
    async getOwnStudentExamIdOf(studentPage: Page, exam: Exam): Promise<number> {
        return await new ExamAPIRequests(studentPage).getOwnStudentExamId(exam);
    }

    /**
     * Gets the id of the logged-in student's own student exam of a real exam.
     */
    async getOwnStudentExamId(exam: Exam): Promise<number> {
        const own = await this.expectOk(
            await this.page.request.get(`api/exam/courses/${exam.course!.id}/exams/${exam.id}/own-student-exam`),
            `get own student exam of exam ${exam.id}`,
        );
        return (await own.json()).id;
    }

    /**
     * Ends one student's attempt in the given number of seconds from now, measured on the server clock, by setting that student
     * exam's working time relative to when the student started. This is how the end of a test exam attempt is set once the student
     * is working, since a test exam attempt runs for its working time from the moment the student starts it.
     * @param exam the exam the student exam belongs to
     * @param studentExamId the student exam to end
     * @param secondsFromNow how long the attempt should still run
     */
    async endStudentExamIn(exam: Exam, studentExamId: number, secondsFromNow: number): Promise<void> {
        const response = await this.expectOk(
            await this.page.request.get(`api/exam/courses/${exam.course!.id}/exams/${exam.id}/student-exams`),
            `get student exams of exam ${exam.id}`,
        );
        const studentExam = ((await response.json()) as StudentExam[]).find((candidate) => candidate.id === studentExamId);
        if (!studentExam?.startedDate) {
            throw new Error(`Student exam ${studentExamId} has not been started, so it has no end to move`);
        }
        const elapsedInSeconds = dayjs(response.headers()['date']).diff(dayjs(studentExam.startedDate as any), 'seconds');
        await this.setStudentExamWorkingTime(exam, studentExamId, elapsedInSeconds + secondsFromNow);
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
     * Triggers the attendance check of a student who is taking the exam: the student is shown a live event with the optional message.
     * @param exam the exam the student takes
     * @param studentLogin the login of the student
     * @param message an optional message for the student
     * @returns the response of the server, so that a test can also check that somebody is not allowed to do this
     */
    async triggerAttendanceCheck(exam: Exam, studentLogin: string, message?: string) {
        return await this.page.request.post(`api/exam/courses/${exam.course!.id}/exams/${exam.id}/students/${studentLogin}/attendance-check`, {
            data: message ?? '',
            headers: { 'Content-Type': 'text/plain' },
        });
    }

    /**
     * Waits until the server clock is past the given instant, with a second of margin for the resolution of the server's `Date`
     * header. Use it to be sure that something the test does next happens after a deadline, e.g. after the regular end of an exam
     * but still inside its grace period.
     * @param exam any exam of the course, only used to reach the server
     * @param instant the deadline to wait for
     */
    async waitUntilServerClockIsAfter(exam: Exam, instant: dayjs.Dayjs) {
        await expect
            .poll(async () => (await this.getExamWithServerTime(exam)).serverNow.isAfter(instant.add(1, 'second')), {
                message: `the server clock should pass ${instant.toISOString()}`,
                intervals: [500],
                timeout: Math.max(instant.diff(dayjs(), 'ms'), 0) + 60_000,
            })
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
     * @param options `publishResults: false` leaves the results unpublished and the review period unset; publish them later with {@link publishResultsIn}
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
     * Ends the exam by shrinking its working time to the time already elapsed, then waits until the exam counts as over
     * (end date plus grace period lies in the past). The server rejects a working time or duration that is not positive, so an
     * exam that has barely started keeps one second and the wait covers the rest.
     */
    async finishExam(exam: Exam) {
        const { current, serverNow } = await this.getExamWithServerTime(exam);
        const startDate = dayjs(current.startDate as any);
        const newDurationInSeconds = Math.max(serverNow.diff(startDate, 'seconds') - (current.gracePeriod ?? 0) - 1, 1);
        const workingTimeChangeInSeconds = newDurationInSeconds - dayjs(current.endDate as any).diff(startDate, 'seconds');
        if (workingTimeChangeInSeconds < 0) {
            await this.changeExamWorkingTime(exam, workingTimeChangeInSeconds);
        }
        await this.waitUntilExamIsOver(exam);
    }
}
