import { HttpClient, HttpErrorResponse, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { faLightbulb } from '@fortawesome/free-solid-svg-icons';
import { captureException } from '@sentry/angular';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamForOverview } from 'app/exam/shared/entities/exam-for-overview.model';
import { convertDateFromServer } from 'app/foundation/util/date.utils';
import { Exercise, ExerciseType, getIcon } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { QuizSubmission } from 'app/quiz/shared/entities/quiz-submission.model';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { StudentExamDTO } from 'app/exam/shared/entities/student-exam-dto.model';
import { toSubmitStudentExamDTO } from 'app/exam/overview/services/submit-student-exam-dto.mapper';
import { Submission, getAllResultsOfAllSubmissions, getLatestSubmissionResult } from 'app/exercise/shared/entities/submission/submission.model';
import { StudentExamWithGradeDTO } from 'app/exam/manage/exam-scores/exam-score-dtos.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import dayjs from 'dayjs/esm';
import { BehaviorSubject, Observable, Subject, of, throwError } from 'rxjs';
import { catchError, map, tap } from 'rxjs/operators';
import { SidebarCardElement } from 'app/foundation/types/sidebar';
import { cloneWith, deepClone } from 'app/foundation/util/deep-clone.util';

export type ButtonTooltipType = 'submitted' | 'submittedSubmissionLimitReached' | 'notSubmitted' | 'synced' | 'notSynced' | 'notSavedOrSubmitted' | 'notStarted';

@Injectable({ providedIn: 'root' })
export class ExamParticipationService {
    private httpClient = inject(HttpClient);
    private localStorageService = inject(LocalStorageService);
    private sessionStorageService = inject(SessionStorageService);

    public currentlyLoadedStudentExam = new Subject<StudentExam>();

    private examIsStartedSubject = new BehaviorSubject<boolean>(false);
    examIsStarted$ = this.examIsStartedSubject.asObservable();

    private testRunSubject = new BehaviorSubject<boolean>(false);
    testRunStarted$ = this.testRunSubject.asObservable();

    private examEndViewSubject = new BehaviorSubject<boolean>(false);
    endViewDisplayed$ = this.examEndViewSubject.asObservable();
    private shouldUpdateTestExams = new BehaviorSubject<boolean>(false);
    shouldUpdateTestExamsObservable = this.shouldUpdateTestExams.asObservable();

    // Version counter bumped whenever a submission's `isSynced` flag is mutated in place (on an answer/model/text
    // change, or when a save succeeds/fails). Submissions are plain mutable objects, so under zoneless change
    // detection those in-place mutations are invisible to signal-based bindings. UI that reflects the sync state
    // (e.g. the in-exercise save button's `disabled`/icon) reads this version to re-evaluate reactively.
    private readonly submissionSyncVersionSignal = signal(0);
    readonly submissionSyncVersion = this.submissionSyncVersionSignal.asReadonly();
    private readonly savingSubmissions = signal<ReadonlySet<Submission>>(new Set());

    /** Notify sync-state-dependent UI that a submission's `isSynced` flag changed (see {@link submissionSyncVersion}). */
    notifySubmissionSyncStateChanged(): void {
        this.submissionSyncVersionSignal.update((version) => version + 1);
    }

    setSubmissionSaving(submission: Submission, saving: boolean): void {
        this.savingSubmissions.update((current) => {
            const updated = new Set(current);
            if (saving) {
                updated.add(submission);
            } else {
                updated.delete(submission);
            }
            return updated;
        });
    }

    isSubmissionSaving(submission: Submission | undefined): boolean {
        return !!submission && this.savingSubmissions().has(submission);
    }

    public getResourceURL(courseId: number, examId: number): string {
        return `api/exam/courses/${courseId}/exams/${examId}`;
    }

    private static getLocalStorageKeyForStudentExam(courseId: number, examId: number): string {
        const prefix = 'artemis_student_exam';
        return `${prefix}_${courseId}_${examId}`;
    }

    /**
     * Retrieves a {@link StudentExam} from server or localstorage. Will also mark the student exam as started
     * @param courseId the id of the course the exam is created in
     * @param examId the id of the exam
     * @param studentExamId the id of the student Exam which should be loaded
     * @returns the studentExam with Exercises for the conduction-phase
     */
    public loadStudentExamWithExercisesForConduction(courseId: number, examId: number, studentExamId: number): Observable<StudentExam | undefined> {
        const url = this.getResourceURL(courseId, examId) + '/student-exams/' + studentExamId + '/conduction';
        return this.getStudentExamFromServer(url).pipe(
            // During the conduction, blocking the student on a failed request would be worse than letting them work on
            // the copy cached on this device, which is a faithful snapshot of the exam they were handed.
            catchError(() => of(this.getStudentExamFromLocalStorage(courseId, examId))),
        );
    }

    /**
     * Retrieves a {@link StudentExam} from the localstorage. Will also mark the student exam as started
     *
     * @param courseId the id of the course the exam is created in
     * @param examId the id of the exam
     */
    public loadStudentExamWithExercisesForConductionFromLocalStorage(courseId: number, examId: number): Observable<StudentExam | undefined> {
        return of(this.getStudentExamFromLocalStorage(courseId, examId));
    }

    /**
     * Retrieves a {@link StudentExam} from the server for display of the summary.
     *
     * Deliberately without a localstorage fallback: the cached exam is the conduction-era copy, i.e. it carries neither
     * results nor scores. Substituting it would present stale data as the current summary once the error toast is gone,
     * so the error is propagated and the caller surfaces a retryable error state instead.
     *
     * @param courseId the id of the course the exam is created in
     * @param examId the id of the exam
     * @param studentExamId the id of the studentExam
     * @returns a studentExam with Exercises for the summary-phase
     */
    public loadStudentExamWithExercisesForSummary(courseId: number, examId: number, studentExamId: number): Observable<StudentExam> {
        const url = this.getResourceURL(courseId, examId) + '/student-exams/' + studentExamId + '/summary';
        return this.getStudentExamFromServer(url);
    }

    /**
     * Retrieves a {@link StudentExam} from the server.
     */
    private getStudentExamFromServer(url: string): Observable<StudentExam> {
        return this.httpClient.get<StudentExam>(url).pipe(
            map((studentExam: StudentExam) => {
                if (studentExam.examSessions && studentExam.examSessions.length > 0 && studentExam.examSessions[0].sessionToken) {
                    this.saveExamSessionTokenToSessionStorage(studentExam.examSessions[0].sessionToken);
                }
                return ExamParticipationService.convertStudentExamFromServer(studentExam);
            }),
            tap((studentExam: StudentExam) => {
                this.currentlyLoadedStudentExam.next(studentExam);
            }),
        );
    }

    /**
     * Retrieves the {@link StudentExam} cached on this device during the conduction, if any.
     */
    private getStudentExamFromLocalStorage(courseId: number, examId: number): StudentExam | undefined {
        return this.localStorageService.retrieve<StudentExam>(ExamParticipationService.getLocalStorageKeyForStudentExam(courseId, examId));
    }

    /**
     * Retrieves a {@link StudentExamWithGradeDTO} without {@link StudentExamWithGradeDTO#studentExam} from server for display of the summary.
     * {@link StudentExamWithGradeDTO#studentExam} is excluded from response to save bandwidth.
     *
     * @param courseId the id of the course the exam is created in
     * @param examId the id of the exam
     * @param userId the id of the student if the current caller is an instructor, the grade info for current user's exam will be retrieved if this argument is empty
     * @param studentExamId the id of the student exam
     */
    public loadStudentExamGradeInfoForSummary(courseId: number, examId: number, studentExamId: number, userId?: number): Observable<StudentExamWithGradeDTO> {
        let params = new HttpParams();
        if (userId) {
            params = params.set('userId', userId.toString());
        }

        const url = `${this.getResourceURL(courseId, examId)}/student-exams/${studentExamId}/grade-summary`;
        return this.httpClient.get<StudentExamWithGradeDTO>(url, { params });
    }

    /**
     * Loads {@link StudentExam} object from server
     * @param courseId the id of the course the exam is created in
     * @param examId the id of the exam
     */
    public getOwnStudentExam(courseId: number, examId: number): Observable<StudentExam> {
        const url = this.getResourceURL(courseId, examId) + '/own-student-exam';
        return this.httpClient.get<StudentExam>(url).pipe(
            map((studentExam: StudentExam) => {
                const convertedStudentExam = ExamParticipationService.convertStudentExamDateFromServer(studentExam);
                this.currentlyLoadedStudentExam.next(convertedStudentExam);
                return convertedStudentExam;
            }),
        );
    }
    /**
     * Fetches the exams of a course that are visible to the current user, for the exams tab of the course overview.
     * These used to arrive as part of the course itself, which made every course visit pay for them.
     * @param courseId the course to fetch the exams for
     */
    public getExamsForOverview(courseId: number): Observable<ExamForOverview[]> {
        return this.httpClient.get<ExamForOverview[]>(`api/exam/courses/${courseId}/exams-for-overview`).pipe(
            map((exams) =>
                exams.map((exam) =>
                    cloneWith(exam, {
                        visibleDate: convertDateFromServer(exam.visibleDate),
                        startDate: convertDateFromServer(exam.startDate),
                        endDate: convertDateFromServer(exam.endDate),
                    }),
                ),
            ),
        );
    }

    public getRealExamSidebarData(courseId: number): Observable<Exam[]> {
        const url = `api/exam/courses/${courseId}/real-exams-sidebar-data`;
        return this.httpClient.get<Exam[]>(url).pipe(
            map((exams: Exam[]) => {
                return exams.map((exam) => ExamParticipationService.convertExamDateFromServer(exam)).filter((exam) => exam !== undefined);
            }),
        );
    }

    public loadTestRunWithExercisesForConduction(courseId: number, examId: number, testRunId: number): Observable<StudentExam> {
        const url = this.getResourceURL(courseId, examId) + '/test-runs/' + testRunId + '/conduction';
        return this.httpClient.get<StudentExam>(url).pipe(
            map((studentExam: StudentExam) => {
                const convertedStudentExam = ExamParticipationService.convertStudentExamDateFromServer(studentExam);
                this.currentlyLoadedStudentExam.next(convertedStudentExam);
                return convertedStudentExam;
            }),
        );
    }

    /**
     * Loads {@link StudentExamDTO} objects linked to a test exam per user and per course from server
     * @param courseId the id of the course we are interested
     * @returns a List of all StudentExams without Exercises per User and Course. Each includes a nested `exam`
     * (id, title, testExam, workingTime, course{id, groupNames}) but no `user`, `exercises`, or `examSessions`.
     */
    public loadStudentExamsForTestExamsPerCourseAndPerUserForOverviewPage(courseId: number): Observable<StudentExamDTO[]> {
        const url = `api/exam/courses/${courseId}/test-exams-per-user`;
        return this.httpClient
            .get<StudentExamDTO[]>(url, { observe: 'response' })
            .pipe(map((studentExam: HttpResponse<StudentExamDTO[]>) => this.processListOfStudentExamsFromServer(studentExam)));
    }

    /**
     * Unlike {@link convertStudentExamDateFromServer}, this does not touch the nested `exam` — the DTO returned by
     * `test-exams-per-user` (`ExamForStudentExamDTO`) never carries date fields (no `startDate`/`endDate`), so there
     * is nothing to convert there.
     */
    private processListOfStudentExamsFromServer(studentExamsResponse: HttpResponse<StudentExamDTO[]>): StudentExamDTO[] {
        studentExamsResponse.body!.forEach((studentExam) => {
            studentExam.submissionDate = studentExam.submissionDate && dayjs(studentExam.submissionDate);
            studentExam.startedDate = studentExam.startedDate && dayjs(studentExam.startedDate);
        });
        return studentExamsResponse.body!;
    }

    /**
     * Submits {@link StudentExam} - the exam cannot be updated afterwards anymore
     * @param courseId the id of the course the exam is created in
     * @param examId the id of the exam
     * @param studentExam the student exam to submit
     */
    public submitStudentExam(courseId: number, examId: number, studentExam: StudentExam): Observable<void> {
        const url = this.getResourceURL(courseId, examId) + '/student-exams/submit';
        const submitStudentExamDTO = toSubmitStudentExamDTO(studentExam);

        return this.httpClient.post<void>(url, submitStudentExamDTO).pipe(
            catchError((error: HttpErrorResponse) => {
                if (error.status === 403 && error.headers.get('x-null-error') === 'error.submissionNotInTime') {
                    return throwError(() => new Error('artemisApp.studentExam.submissionNotInTime'));
                } else if (error.status === 409 && error.headers.get('x-null-error') === 'error.alreadySubmitted') {
                    return throwError(() => new Error('artemisApp.studentExam.alreadySubmitted'));
                } else {
                    return throwError(() => new Error('artemisApp.studentExam.handInFailed'));
                }
            }),
        );
    }

    /**
     * Requests Athena AI feedback for all text and modeling exercises in the given submitted test exam.
     *
     * @param courseId the id of the course the exam is created in
     * @param examId the id of the exam
     * @param studentExamId the id of the submitted student exam to request feedback for
     * @returns an observable that completes once the feedback dispatch has been triggered server-side
     */
    public requestAthenaFeedback(courseId: number, examId: number, studentExamId: number): Observable<void> {
        const url = `${this.getResourceURL(courseId, examId)}/student-exams/${studentExamId}/request-feedback`;
        return this.httpClient.post<void>(url, null);
    }

    /**
     * Fetches how many Athena AI feedback requests the current user has used for this test exam and the configured cap.
     *
     * @param courseId the id of the course the exam is created in
     * @param examId the id of the exam
     * @param studentExamId the id of the student exam whose usage should be retrieved
     * @returns an observable emitting the number of used requests and the configured request limit
     */
    public getAthenaFeedbackUsage(courseId: number, examId: number, studentExamId: number): Observable<{ used: number; limit: number }> {
        const url = `${this.getResourceURL(courseId, examId)}/student-exams/${studentExamId}/athena-feedback-usage`;
        return this.httpClient.get<{ used: number; limit: number }>(url);
    }

    private static breakCircularDependency(studentExam: StudentExam) {
        studentExam.exercises!.forEach((exercise) => {
            if (exercise.studentParticipations) {
                for (const participation of exercise.studentParticipations) {
                    const results = getAllResultsOfAllSubmissions(participation.submissions);
                    if (results) {
                        for (const result of results) {
                            if (result.feedbacks) {
                                for (const feedback of result.feedbacks) {
                                    delete feedback.result;
                                }
                            }
                        }
                    }
                    if (participation.submissions) {
                        for (const submission of participation.submissions) {
                            delete submission.participation;
                            const result = getLatestSubmissionResult(submission);
                            if (result) {
                                delete result.submission;
                            }
                        }
                    }
                    delete participation.exercise;
                }
            }
        });
    }

    /**
     * save the studentExam to the local Storage
     *
     * @param courseId
     * @param examId
     * @param studentExam
     */
    public saveStudentExamToLocalStorage(courseId: number, examId: number, studentExam: StudentExam): void {
        // if the following code fails, this should never affect the exam
        try {
            const studentExamCopy = deepClone(studentExam);
            ExamParticipationService.breakCircularDependency(studentExamCopy);
            this.localStorageService.store(ExamParticipationService.getLocalStorageKeyForStudentExam(courseId, examId), studentExamCopy);
        } catch (error) {
            captureException(error);
        }
    }

    /**
     * saves latest examSessionToken to sessionStorage
     * @param examSessionToken latest examSessionToken
     */
    public saveExamSessionTokenToSessionStorage(examSessionToken: string): void {
        this.sessionStorageService.store('ExamSessionToken', examSessionToken);
    }

    /**
     * Update a quizSubmission
     *
     * @param exerciseId
     * @param quizSubmission
     */
    public updateQuizSubmission(exerciseId: number, quizSubmission: QuizSubmission): Observable<QuizSubmission> {
        const url = `api/quiz/exercises/${exerciseId}/submissions/exam`;
        return this.httpClient.put<QuizSubmission>(url, quizSubmission);
    }

    public setLastSaveFailed(saveFailed: boolean, courseId: number, examId: number): void {
        const key = ExamParticipationService.getLocalStorageKeyForStudentExam(courseId, examId) + '-save-failed';
        this.localStorageService.store(key, saveFailed);
    }

    public lastSaveFailed(courseId: number, examId: number): boolean {
        const key = ExamParticipationService.getLocalStorageKeyForStudentExam(courseId, examId) + '-save-failed';
        return this.localStorageService.retrieve<boolean>(key) || false;
    }

    private static convertStudentExamFromServer(studentExam: StudentExam): StudentExam {
        studentExam.exercises = ExerciseService.convertExercisesDateFromServer(studentExam.exercises);
        studentExam.exam = ExamParticipationService.convertExamDateFromServer(studentExam.exam);
        // Add a default exercise group to connect exercises with the exam.
        studentExam.exercises = studentExam.exercises.map((exercise: Exercise) => {
            // Built field by field, not copied: the group's `exercises` are the very objects being mapped here, so they
            // must stay the same instances. `?? new ExerciseGroup()` keeps the previous spread behaviour, which produced
            // a group carrying only the exam when the server sent none.
            const group = exercise.exerciseGroup ?? new ExerciseGroup();
            const groupWithExam = new ExerciseGroup();
            groupWithExam.id = group.id;
            groupWithExam.title = group.title;
            groupWithExam.isMandatory = group.isMandatory;
            groupWithExam.exercises = group.exercises;
            groupWithExam.exam = studentExam.exam;
            exercise.exerciseGroup = groupWithExam;
            return exercise;
        });
        return studentExam;
    }

    private static convertExamDateFromServer(exam?: Exam) {
        if (exam) {
            exam.visibleDate = exam.visibleDate ? dayjs(exam.visibleDate) : undefined;
            exam.startDate = exam.startDate ? dayjs(exam.startDate) : undefined;
            exam.endDate = exam.endDate ? dayjs(exam.endDate) : undefined;
            exam.publishResultsDate = exam.publishResultsDate ? dayjs(exam.publishResultsDate) : undefined;
            exam.examStudentReviewStart = exam.examStudentReviewStart ? dayjs(exam.examStudentReviewStart) : undefined;
            exam.examStudentReviewEnd = exam.examStudentReviewEnd ? dayjs(exam.examStudentReviewEnd) : undefined;
            exam.examSummaryPublicationDate = exam.examSummaryPublicationDate ? dayjs(exam.examSummaryPublicationDate) : undefined;
        }
        return exam;
    }

    private static convertStudentExamDateFromServer(studentExam: StudentExam): StudentExam {
        studentExam.exam = ExamParticipationService.convertExamDateFromServer(studentExam.exam);
        studentExam.submissionDate = studentExam.submissionDate && dayjs(studentExam.submissionDate);
        studentExam.startedDate = studentExam.startedDate && dayjs(studentExam.startedDate);
        return studentExam;
    }

    public static getSubmissionForExercise(exercise: Exercise): Submission | undefined {
        const studentParticipation = ExamParticipationService.getParticipationForExercise(exercise);
        if (studentParticipation && studentParticipation.submissions) {
            // NOTE: using "submissions[0]" might not work for programming exercises with multiple submissions, it is better to always take the last submission
            return studentParticipation.submissions.last();
        }
        return undefined;
    }

    /**
     * Get the first participation for the given exercise.
     * @param exercise the exercise for which to get the participation
     * @return the first participation of the given exercise
     */
    public static getParticipationForExercise(exercise: Exercise): StudentParticipation | undefined {
        if (exercise && exercise.studentParticipations && exercise.studentParticipations.length > 0) {
            return exercise.studentParticipations[0];
        }
        return undefined;
    }

    getExerciseButtonTooltip(exercise: Exercise): ButtonTooltipType {
        const submission = ExamParticipationService.getSubmissionForExercise(exercise);
        // The submission might not yet exist for this exercise.
        // When the participant navigates to the exercise the submissions are created.
        // Until then show, that the exercise is synced
        if (!submission) {
            return 'synced';
        }
        if (exercise.type !== ExerciseType.PROGRAMMING) {
            if (submission.submitted) {
                return submission.isSynced ? 'synced' : 'notSynced';
            } else {
                return submission.isSynced ? 'notStarted' : 'notSynced';
            }
        }
        if (submission.submitted && submission.isSynced) {
            return 'submitted'; // You have submitted an exercise. You can submit again
        } else if (!submission.submitted && submission.isSynced) {
            return 'notSubmitted'; // starting point
        } else {
            return 'notSavedOrSubmitted';
        }
    }

    setEndView(isEndView: boolean) {
        this.examEndViewSubject.next(isEndView);
    }
    setShouldUpdateTestExams(shouldUpdate: boolean) {
        this.shouldUpdateTestExams.next(shouldUpdate);
    }

    setExamLayout(isExamStarted: boolean = true, isTestRun: boolean = false) {
        this.examIsStartedSubject.next(isExamStarted);
        this.testRunSubject.next(isTestRun);
    }

    resetExamLayout() {
        this.examIsStartedSubject.next(false);
        this.testRunSubject.next(false);
        document.documentElement.style.setProperty('--header-height', '68px'); // Set back to default value, because exam nav bar changes this property within the exam
    }

    mapExercisesToSidebarCardElements(exercises: Exercise[]) {
        return exercises.map((exercise) => this.mapExerciseToSidebarCardElement(exercise));
    }

    mapExerciseToSidebarCardElement(exercise: Exercise): SidebarCardElement {
        return {
            title: exercise.exerciseGroup?.title ?? '',
            id: exercise.id ?? '',
            icon: getIcon(exercise.type),
            rightIcon: faLightbulb,
            size: 'M',
        };
    }
}
