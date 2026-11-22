import { HttpErrorResponse } from '@angular/common/http';
import { Service, inject, signal } from '@angular/core';
import { faLightbulb } from '@fortawesome/free-solid-svg-icons';
import { captureException } from 'app/core/error-reporting/capture-exception';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { isRealExam } from 'app/exam/overview/exam.utils';
import { ExamForOverview } from 'app/exam/shared/entities/exam-for-overview.model';
import { ExamApi } from 'app/openapi/api/exam-api';
import { StudentExamApi } from 'app/openapi/api/student-exam-api';
import { StudentExamConductionApi } from 'app/openapi/api/student-exam-conduction-api';
import type { OwnStudentExam } from 'app/openapi/model/own-student-exam';
import type { StudentExamForConduction } from 'app/openapi/model/student-exam-for-conduction';
import type { StudentExamForSummary } from 'app/openapi/model/student-exam-for-summary';
import { toExamsForOverview, toSidebarExam, toStudentExamDTOs } from 'app/exam/overview/util/generated-exam-participation.util';
import { Exercise, ExerciseType, getIcon } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { StudentExamOrDTO } from 'app/exam/shared/entities/student-exam-dto.model';
import { toSubmitStudentExamDTO } from 'app/exam/overview/services/submit-student-exam-dto.mapper';
import { Submission, getAllResultsOfAllSubmissions, getLatestSubmissionResult } from 'app/exercise/shared/entities/submission/submission.model';
import { StudentExamWithGradeDTO } from 'app/exam/manage/exam-scores/exam-score-dtos.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import dayjs from 'dayjs/esm';
import { BehaviorSubject, Observable, Subject, of, throwError } from 'rxjs';
import { catchError, filter, map, tap } from 'rxjs/operators';
import { SidebarCardElement } from 'app/foundation/types/sidebar';
import { deepClone, hydrate } from 'app/foundation/util/deep-clone.util';

export type ButtonTooltipType = 'submitted' | 'submittedSubmissionLimitReached' | 'notSubmitted' | 'synced' | 'notSynced' | 'notSavedOrSubmitted' | 'notStarted';

@Service()
export class ExamParticipationService {
    private examApi = inject(ExamApi);
    private studentExamApi = inject(StudentExamApi);
    private studentExamConductionApi = inject(StudentExamConductionApi);
    private localStorageService = inject(LocalStorageService);
    private sessionStorageService = inject(SessionStorageService);

    public currentlyLoadedStudentExam = new Subject<StudentExam>();

    private examIsStartedSubject = new BehaviorSubject<boolean>(false);
    examIsStarted$ = this.examIsStartedSubject.asObservable();

    private testRunSubject = new BehaviorSubject<boolean>(false);
    testRunStarted$ = this.testRunSubject.asObservable();

    private examEndViewSubject = new BehaviorSubject<boolean>(false);
    endViewDisplayed$ = this.examEndViewSubject.asObservable();

    readonly testStudentExams = signal<StudentExamOrDTO[]>([]);

    constructor() {
        this.currentlyLoadedStudentExam.pipe(filter((studentExam) => !!studentExam?.submitted && !!studentExam.exam && !isRealExam(studentExam.exam))).subscribe((latestExam) => {
            this.testStudentExams.update((studentExams) => {
                const index = studentExams.findIndex((se) => se?.id === latestExam?.id);
                if (index !== -1) {
                    const updated = [...studentExams];
                    updated[index] = latestExam;
                    return updated;
                }
                return [...studentExams, latestExam];
            });
        });
    }

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
    public loadStudentExamWithExercisesForConduction(
        courseId: number,
        examId: number,
        studentExamId: number,
        purpose: 'student' | 'instructor-owner' = 'student',
    ): Observable<StudentExam | undefined> {
        const request =
            purpose === 'instructor-owner'
                ? this.studentExamConductionApi.getStudentExamForInstructorConduction(courseId, examId, studentExamId)
                : this.studentExamConductionApi.getStudentExamForConduction(courseId, examId, studentExamId);
        return this.getStudentExamFromServer(request).pipe(
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
        return this.getStudentExamFromServer(this.studentExamApi.getStudentExamForSummary(courseId, examId, studentExamId));
    }

    /**
     * Retrieves a {@link StudentExam} from the server.
     */
    private getStudentExamFromServer(request: Observable<StudentExamForConduction | StudentExamForSummary>): Observable<StudentExam> {
        return request.pipe(
            map((response) => {
                const studentExam = ExamParticipationService.toStudentExam(response);
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
     * @param studentExamId the id of the student exam
     */
    public loadStudentExamGradeInfoForSummary(courseId: number, examId: number, studentExamId: number): Observable<StudentExamWithGradeDTO> {
        return this.studentExamApi.getStudentExamGradesForSummary(courseId, examId, studentExamId).pipe(map((grades) => hydrate(new StudentExamWithGradeDTO(), grades)));
    }

    /**
     * Loads {@link StudentExam} object from server
     * @param courseId the id of the course the exam is created in
     * @param examId the id of the exam
     */
    public getOwnStudentExam(courseId: number, examId: number): Observable<StudentExam> {
        return this.examApi.getOwnStudentExam(courseId, examId).pipe(
            map((response) => {
                const convertedStudentExam = ExamParticipationService.convertStudentExamDateFromServer(ExamParticipationService.toStudentExam(response));
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
        return this.examApi.getExamsForCourseOverview(courseId).pipe(map((exams) => toExamsForOverview(exams)));
    }

    public getRealExamSidebarData(courseId: number): Observable<Exam[]> {
        return this.examApi.getSidebarDataForRealExams(courseId).pipe(map((exams) => exams.map((exam) => toSidebarExam(exam))));
    }

    public loadTestRunWithExercisesForConduction(courseId: number, examId: number, testRunId: number): Observable<StudentExam> {
        return this.studentExamConductionApi.getTestRunForConduction(courseId, examId, testRunId).pipe(
            map((response) => {
                const convertedStudentExam = ExamParticipationService.convertStudentExamDateFromServer(ExamParticipationService.toStudentExam(response));
                this.currentlyLoadedStudentExam.next(convertedStudentExam);
                return convertedStudentExam;
            }),
        );
    }

    /**
     * Loads {@link StudentExamDTO} objects linked to a test exam per user and per course from server and stores them in the service.
     * @param courseId the id of the course we are interested
     */
    public loadStudentExamsForTestExamsPerCourseAndPerUserForOverviewPage(courseId: number): Observable<void> {
        return this.studentExamApi.getStudentExamsForCoursePerUser(courseId).pipe(
            map((studentExams) => toStudentExamDTOs(studentExams)),
            tap((studentExams) => this.testStudentExams.set(studentExams)),
            map(() => {}),
        );
    }

    /**
     * Submits {@link StudentExam} - the exam cannot be updated afterwards anymore
     * @param courseId the id of the course the exam is created in
     * @param examId the id of the exam
     * @param studentExam the student exam to submit
     */
    public submitStudentExam(courseId: number, examId: number, studentExam: StudentExam): Observable<void> {
        return this.studentExamApi.submitStudentExam(courseId, examId, toSubmitStudentExamDTO(studentExam)).pipe(
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

    public setLastSaveFailed(saveFailed: boolean, courseId: number, examId: number): void {
        const key = ExamParticipationService.getLocalStorageKeyForStudentExam(courseId, examId) + '-save-failed';
        this.localStorageService.store(key, saveFailed);
    }

    public lastSaveFailed(courseId: number, examId: number): boolean {
        const key = ExamParticipationService.getLocalStorageKeyForStudentExam(courseId, examId) + '-save-failed';
        return this.localStorageService.retrieve<boolean>(key) || false;
    }

    /**
     * Gives a conduction response the prototype of {@link StudentExam}. The class sets `ended = false`, but the server
     * omits `ended` when it cannot decide, and the client then falls back to its own time check, so an omitted value
     * must stay undefined.
     */
    private static toStudentExam(response: StudentExamForConduction | StudentExamForSummary | OwnStudentExam): StudentExam {
        const studentExam = hydrate(new StudentExam(), response);
        if (response.ended === undefined) {
            studentExam.ended = undefined;
        }
        return studentExam;
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
