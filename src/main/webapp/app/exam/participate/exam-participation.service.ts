import { Injectable } from '@angular/core';
import { Observable, of, Subject, throwError } from 'rxjs';
import { StudentExam } from 'app/entities/student-exam.model';
import { HttpClient, HttpErrorResponse, HttpResponse, HttpParams } from '@angular/common/http';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { QuizSubmission } from 'app/entities/quiz/quiz-submission.model';
import { catchError, map } from 'rxjs/operators';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { Exam } from 'app/entities/exam.model';
import dayjs from 'dayjs/esm';
import { getLatestSubmissionResult } from 'app/entities/submission.model';
import { cloneDeep } from 'lodash-es';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { StudentExamWithGradeDTO } from 'app/exam/exam-scores/exam-score-dtos.model';

@Injectable({ providedIn: 'root' })
export class ExamParticipationService {
    public currentlyLoadedStudentExam = new Subject<StudentExam>();

    private examExerciseIds: number[];

    public getResourceURL(courseId: number, examId: number): string {
        return `${SERVER_API_URL}api/courses/${courseId}/exams/${examId}`;
    }

    constructor(private httpClient: HttpClient, private localStorageService: LocalStorageService, private sessionStorage: SessionStorageService) {}

    private static getLocalStorageKeyForStudentExam(courseId: number, examId: number): string {
        const prefix = 'artemis_student_exam';
        return `${prefix}_${courseId}_${examId}`;
    }

    /**
     * Retrieves a {@link StudentExam} from server or localstorage. Will also mark the student exam as started
     * @param courseId the id of the course the exam is created in
     * @param examId the id of the exam
     */
    public loadStudentExamWithExercisesForConduction(courseId: number, examId: number): Observable<StudentExam> {
        const url = this.getResourceURL(courseId, examId) + '/student-exams/conduction';
        return this.getStudentExamFromServer(url, courseId, examId);
    }

    /**
     * Retrieves a {@link StudentExam} from server or localstorage. Will also mark the student exam as started
     * @param courseId the id of the course the exam is created in
     * @param examId the id of the exam
     * @param studentExamId the id of the student Exam which should be loaded
     * @returns the studentExam with Exercises for the conduction-phase
     */
    public loadStudentExamWithExercisesForConductionOfTestExam(courseId: number, examId: number, studentExamId: number): Observable<StudentExam> {
        const url = this.getResourceURL(courseId, examId) + '/student-exams/' + studentExamId + '/conduction';
        return this.getStudentExamFromServer(url, courseId, examId);
    }

    /**
     * Retrieves a {@link StudentExam} from the localstorage. Will also mark the student exam as started
     *
     * @param courseId the id of the course the exam is created in
     * @param examId the id of the exam
     */
    public loadStudentExamWithExercisesForConductionFromLocalStorage(courseId: number, examId: number): Observable<StudentExam> {
        const localStoredExam: StudentExam = JSON.parse(this.localStorageService.retrieve(ExamParticipationService.getLocalStorageKeyForStudentExam(courseId, examId)));
        return of(localStoredExam);
    }

    /**
     * Retrieves a {@link StudentExam} from server or localstorage for display of the summary.
     * @param courseId the id of the course the exam is created in
     * @param examId the id of the exam
     */
    public loadStudentExamWithExercisesForSummary(courseId: number, examId: number): Observable<StudentExam> {
        const url = this.getResourceURL(courseId, examId) + '/student-exams/summary';
        return this.getStudentExamFromServer(url, courseId, examId);
    }

    /**
     * Retrieves a {@link StudentExam} from server or localstorage for display of the summary.
     * Needed to display the summary for test exams, as one student can have multiple studentExams.
     * @param courseId the id of the course the exam is created in
     * @param examId the id of the exam
     * @param studentExamId the id of the studentExam
     * @returns a studentExam with Exercises for the summary-phase
     */
    public loadStudentExamForTestExamWithExercisesForSummary(courseId: number, examId: number, studentExamId: number): Observable<StudentExam> {
        const url = this.getResourceURL(courseId, examId) + '/student-exams/' + studentExamId + '/summary';
        return this.getStudentExamFromServer(url, courseId, examId);
    }

    /**
     * Retrieves a {@link StudentExam} from server or localstorage.
     */
    private getStudentExamFromServer(url: string, courseId: number, examId: number): Observable<StudentExam> {
        return this.httpClient.get<StudentExam>(url).pipe(
            map((studentExam: StudentExam) => {
                if (studentExam.examSessions && studentExam.examSessions.length > 0 && studentExam.examSessions[0].sessionToken) {
                    this.saveExamSessionTokenToSessionStorage(studentExam.examSessions[0].sessionToken);
                }
                return ExamParticipationService.convertStudentExamFromServer(studentExam);
            }),
            catchError(() => {
                const localStoredExam: StudentExam = JSON.parse(this.localStorageService.retrieve(ExamParticipationService.getLocalStorageKeyForStudentExam(courseId, examId)));
                return of(localStoredExam);
            }),
        );
    }

    /**
     * Retrieves a {@link StudentExamWithGradeDTO} without {@link StudentExamWithGradeDTO#studentExam} from server for display of the summary.
     * {@link StudentExamWithGradeDTO#studentExam} is excluded from response to save bandwidth.
     *
     * @param courseId the id of the course the exam is created in
     * @param examId the id of the exam
     * @param userId the id of the student if the current caller is an instructor, the grade info for current user's exam will be retrieved if this argument is empty
     */
    public loadStudentExamGradeInfoForSummary(courseId: number, examId: number, userId?: number): Observable<StudentExamWithGradeDTO> {
        let params = new HttpParams();
        if (userId) {
            params = params.set('userId', userId.toString());
        }
        const url = this.getResourceURL(courseId, examId) + '/student-exams/grade-summary';
        return this.httpClient.get<StudentExamWithGradeDTO>(url, { params });
    }

    /**
     * Loads {@link Exam} object from server
     * @param courseId the id of the course the exam is created in
     * @param examId the id of the exam
     */
    public loadStudentExam(courseId: number, examId: number): Observable<StudentExam> {
        const url = this.getResourceURL(courseId, examId) + '/start';
        return this.httpClient.get<StudentExam>(url).pipe(
            map((studentExam: StudentExam) => {
                const convertedStudentExam = ExamParticipationService.convertStudentExamDateFromServer(studentExam);
                this.currentlyLoadedStudentExam.next(convertedStudentExam);
                return convertedStudentExam;
            }),
        );
    }

    /**
     * Loads {@link StudentExam} object for a test exam from server
     * @param courseId the id of the course the exam is created in
     * @param examId the id of the exam
     * @returns a studentExam without exercises for the start-phase
     */
    public loadStudentExamForTestExam(courseId: number, examId: number): Observable<StudentExam> {
        const url = this.getResourceURL(courseId, examId) + '/start-test-exam';
        return this.httpClient.get<StudentExam>(url).pipe(
            map((studentExam: StudentExam) => {
                const convertedStudentExam = ExamParticipationService.convertStudentExamDateFromServer(studentExam);
                this.currentlyLoadedStudentExam.next(convertedStudentExam);
                return convertedStudentExam;
            }),
        );
    }

    /**
     * Loads {@link StudentExam} object for a test exam from server
     * @param courseId the id of the course the exam is created in
     * @param examId the id of the exam
     * @param studentExamId the id of the studentExam we want to fetch
     * @returns the StudentExam for the specified id without Exercises for the start-phase
     */
    public loadStudentExamForTestExamById(courseId: number, examId: number, studentExamId: number): Observable<StudentExam> {
        const url = this.getResourceURL(courseId, examId) + '/test-exam/' + studentExamId + '/start';
        return this.httpClient.get<StudentExam>(url).pipe(
            map((studentExam: StudentExam) => {
                const convertedStudentExam = ExamParticipationService.convertStudentExamDateFromServer(studentExam);
                this.currentlyLoadedStudentExam.next(convertedStudentExam);
                return convertedStudentExam;
            }),
        );
    }

    public loadTestRunWithExercisesForConduction(courseId: number, examId: number, testRunId: number): Observable<StudentExam> {
        const url = this.getResourceURL(courseId, examId) + '/test-run/' + testRunId + '/conduction';
        return this.httpClient.get<StudentExam>(url).pipe(
            map((studentExam: StudentExam) => {
                const convertedStudentExam = ExamParticipationService.convertStudentExamDateFromServer(studentExam);
                this.currentlyLoadedStudentExam.next(convertedStudentExam);
                return convertedStudentExam;
            }),
        );
    }

    /**
     * Loads {@link StudentExam} objects linked to a test exam per user and per course from server
     * @param courseId the id of the course we are interested
     * @returns a List of all StudentExams without Exercises per User and Course
     */
    public loadStudentExamsForTestExamsPerCourseAndPerUserForOverviewPage(courseId: number): Observable<StudentExam[]> {
        const url = `${SERVER_API_URL}api/courses/${courseId}/test-exams-per-user`;
        return this.httpClient
            .get<StudentExam[]>(url, { observe: 'response' })
            .pipe(map((studentExam: HttpResponse<StudentExam[]>) => this.processListOfStudentExamsFromServer(studentExam)));
    }

    private processListOfStudentExamsFromServer(studentExamsResponse: HttpResponse<StudentExam[]>) {
        studentExamsResponse.body!.forEach((studentExam) => {
            return ExamParticipationService.convertStudentExamDateFromServer(studentExam);
        });
        return studentExamsResponse.body!;
    }

    /**
     * Submits {@link StudentExam} - the exam cannot be updated afterwards anymore
     * @param courseId the id of the course the exam is created in
     * @param examId the id of the exam
     * @param studentExam: the student exam to submit
     * @return returns the studentExam version of the server
     */
    public submitStudentExam(courseId: number, examId: number, studentExam: StudentExam): Observable<StudentExam> {
        const url = this.getResourceURL(courseId, examId) + '/student-exams/submit';
        const studentExamCopy = cloneDeep(studentExam);
        ExamParticipationService.breakCircularDependency(studentExamCopy);

        return this.httpClient.post<StudentExam>(url, studentExamCopy).pipe(
            map((submittedStudentExam: StudentExam) => {
                return ExamParticipationService.convertStudentExamFromServer(submittedStudentExam);
            }),
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
            if (!!exercise.studentParticipations) {
                for (const participation of exercise.studentParticipations) {
                    if (!!participation.results) {
                        for (const result of participation.results) {
                            delete result.participation;
                        }
                    }
                    if (!!participation.submissions) {
                        for (const submission of participation.submissions) {
                            delete submission.participation;
                            const result = getLatestSubmissionResult(submission);
                            if (!!result) {
                                delete result.participation;
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
        const studentExamCopy = cloneDeep(studentExam);
        ExamParticipationService.breakCircularDependency(studentExamCopy);
        this.localStorageService.store(ExamParticipationService.getLocalStorageKeyForStudentExam(courseId, examId), JSON.stringify(studentExamCopy));
    }

    /**
     * saves latest examSessionToken to sessionStorage
     * @param examSessionToken latest examSessionToken
     */
    public saveExamSessionTokenToSessionStorage(examSessionToken: string): void {
        this.sessionStorage.store('ExamSessionToken', examSessionToken);
    }

    /**
     * Update a quizSubmission
     *
     * @param exerciseId
     * @param quizSubmission
     */
    public updateQuizSubmission(exerciseId: number, quizSubmission: QuizSubmission): Observable<QuizSubmission> {
        const url = `${SERVER_API_URL}api/exercises/${exerciseId}/submissions/exam`;
        return this.httpClient.put<QuizSubmission>(url, quizSubmission);
    }

    public setLastSaveFailed(saveFailed: boolean, courseId: number, examId: number): void {
        const key = ExamParticipationService.getLocalStorageKeyForStudentExam(courseId, examId) + '-save-failed';
        this.localStorageService.store(key, saveFailed);
    }

    public lastSaveFailed(courseId: number, examId: number): boolean {
        const key = ExamParticipationService.getLocalStorageKeyForStudentExam(courseId, examId) + '-save-failed';
        return this.localStorageService.retrieve(key);
    }

    private static convertStudentExamFromServer(studentExam: StudentExam): StudentExam {
        studentExam.exercises = ExerciseService.convertExercisesDateFromServer(studentExam.exercises);
        studentExam.exam = ExamParticipationService.convertExamDateFromServer(studentExam.exam);
        // Add a default exercise group to connect exercises with the exam.
        studentExam.exercises = studentExam.exercises.map((exercise: Exercise) => {
            if (!exercise.exerciseGroup) {
                exercise.exerciseGroup = { exam: studentExam.exam } as ExerciseGroup;
            }
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
        }
        return exam;
    }

    private static convertStudentExamDateFromServer(studentExam: StudentExam): StudentExam {
        studentExam.exam = ExamParticipationService.convertExamDateFromServer(studentExam.exam);
        studentExam.submissionDate = studentExam.submissionDate && dayjs(studentExam.submissionDate);
        studentExam.startedDate = studentExam.startedDate && dayjs(studentExam.startedDate);
        return studentExam;
    }

    public static getSubmissionForExercise(exercise: Exercise) {
        if (exercise && exercise.studentParticipations && exercise.studentParticipations.length > 0 && exercise.studentParticipations[0].submissions) {
            // NOTE: using "submissions[0]" might not work for programming exercises with multiple submissions, it is better to always take the last submission
            return exercise.studentParticipations[0].submissions.last();
        }
    }

    getExerciseButtonTooltip(exercise: Exercise): 'submitted' | 'notSubmitted' | 'synced' | 'notSynced' | 'notSavedOrSubmitted' {
        const submission = ExamParticipationService.getSubmissionForExercise(exercise);
        // The submission might not yet exist for this exercise.
        // When the participant navigates to the exercise the submissions are created.
        // Until then show, that the exercise is synced
        if (!submission) {
            return 'synced';
        }
        if (exercise.type !== ExerciseType.PROGRAMMING) {
            return submission.isSynced ? 'synced' : 'notSynced';
        }
        // programming exercise
        if (submission.submitted && submission.isSynced) {
            return 'submitted'; // You have submitted an exercise. You can submit again
        } else if (!submission.submitted && submission.isSynced) {
            return 'notSubmitted'; // starting point
        } else {
            return 'notSavedOrSubmitted';
        }
    }

    public getExamExerciseIds(): number[] {
        return this.examExerciseIds;
    }

    public setExamExerciseIds(examExerciseIds: number[]) {
        this.examExerciseIds = examExerciseIds;
    }
}
