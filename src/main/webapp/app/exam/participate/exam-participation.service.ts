import { Injectable } from '@angular/core';
import { SERVER_API_URL } from 'app/app.constants';
import { Observable, Subject, throwError } from 'rxjs';
import { StudentExam } from 'app/entities/student-exam.model';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { QuizSubmission } from 'app/entities/quiz/quiz-submission.model';
import { catchError, map } from 'rxjs/operators';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { Exam } from 'app/entities/exam.model';
import * as moment from 'moment';

@Injectable({ providedIn: 'root' })
export class ExamParticipationService {
    public currentlyLoadedStudentExam = new Subject<StudentExam>();

    public getResourceURL(courseId: number, examId: number): string {
        return `${SERVER_API_URL}api/courses/${courseId}/exams/${examId}`;
    }

    constructor(
        private httpClient: HttpClient,
        private localStorageService: LocalStorageService,
        private sessionStorage: SessionStorageService,
        private exerciseService: ExerciseService,
    ) {}

    private getLocalStorageKeyForStudentExam(courseId: number, examId: number): string {
        const prefix = 'artemis_student_exam';
        return `${prefix}_${courseId}_${examId}`;
    }

    /**
     * Retrieves a {@link StudentExam} from server or localstorage. Will also mark the student exam as started
     * @param courseId the id of the course the exam is created in
     * @param examId the id of the exam
     */
    public loadStudentExamWithExercisesForConduction(courseId: number, examId: number): Observable<StudentExam> {
        const url = this.getResourceURL(courseId, examId) + '/studentExams/conduction';
        return this.getStudentExamFromServer(url, courseId, examId);
    }

    /**
     * Retrieves a {@link StudentExam} from server or localstorage for display of the summary.
     * @param courseId the id of the course the exam is created in
     * @param examId the id of the exam
     */
    public loadStudentExamWithExercisesForSummary(courseId: number, examId: number): Observable<StudentExam> {
        const url = this.getResourceURL(courseId, examId) + '/studentExams/summary';
        return this.getStudentExamFromServer(url, courseId, examId);
    }

    /**
     * Retrieves a {@link StudentExam} from server or localstorage.
     */
    private getStudentExamFromServer(url: string, courseId: number, examId: number): Observable<StudentExam> {
        return this.httpClient.get<StudentExam>(url).pipe(
            map((studentExam: StudentExam) => {
                if (studentExam.examSessions) {
                    this.saveExamSessionTokenToSessionStorage(studentExam.examSessions[0].sessionToken);
                }

                return this.convertStudentExamFromServer(studentExam);
            }),
            catchError(() => {
                const localStoredExam: StudentExam = JSON.parse(this.localStorageService.retrieve(this.getLocalStorageKeyForStudentExam(courseId, examId)));
                return Observable.of(localStoredExam);
            }),
        );
    }

    /**
     * Loads {@link Exam} object from server
     * @param courseId the id of the course the exam is created in
     * @param examId the id of the exam
     */
    public loadStudentExam(courseId: number, examId: number): Observable<StudentExam> {
        const url = this.getResourceURL(courseId, examId) + '/conduction';
        return this.httpClient.get<StudentExam>(url).map((studentExam: StudentExam) => {
            const convertedStudentExam = this.convertStudentExamDateFromServer(studentExam);
            this.currentlyLoadedStudentExam.next(convertedStudentExam);
            return convertedStudentExam;
        });
    }

    /**
     * Submits {@link StudentExam} - the exam cannot be updated afterwards anymore
     * @param courseId the id of the course the exam is created in
     * @param examId the id of the exam
     * @param studentExam: the student exam to submit
     * @return returns the studentExam verison of the server
     */
    public submitStudentExam(courseId: number, examId: number, studentExam: StudentExam): Observable<StudentExam> {
        const url = this.getResourceURL(courseId, examId) + '/studentExams/submit';
        return this.httpClient.post<StudentExam>(url, studentExam).pipe(
            map((submittedStudentExam: StudentExam) => {
                return this.convertStudentExamFromServer(submittedStudentExam);
            }),
            catchError((error: HttpErrorResponse) => {
                if (error.status === 403 && error.headers.get('x-null-error') === 'error.submissionNotInTime') {
                    return throwError(new Error('studentExam.submissionNotInTime'));
                } else {
                    return throwError(new Error('studentExam.handInFailed'));
                }
            }),
        );
    }

    /**
     * save the studentExam to the local Storage
     *
     * @param courseId
     * @param examId
     * @param studentExam
     */
    public saveStudentExamToLocalStorage(courseId: number, examId: number, studentExam: StudentExam): void {
        this.localStorageService.store(this.getLocalStorageKeyForStudentExam(courseId, examId), JSON.stringify(studentExam));
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

    private convertStudentExamFromServer(studentExam: StudentExam): StudentExam {
        studentExam.exercises = this.exerciseService.convertExercisesDateFromServer(studentExam.exercises);
        studentExam.exam = this.convertExamDateFromServer(studentExam.exam);
        return studentExam;
    }

    private convertExamDateFromServer(exam: Exam): Exam {
        exam.visibleDate = exam.visibleDate ? moment(exam.visibleDate) : null;
        exam.startDate = exam.startDate ? moment(exam.startDate) : null;
        exam.endDate = exam.endDate ? moment(exam.endDate) : null;
        exam.publishResultsDate = exam.publishResultsDate ? moment(exam.publishResultsDate) : null;
        exam.examStudentReviewStart = exam.examStudentReviewStart ? moment(exam.examStudentReviewStart) : null;
        exam.examStudentReviewEnd = exam.examStudentReviewEnd ? moment(exam.examStudentReviewEnd) : null;
        return exam;
    }

    private convertStudentExamDateFromServer(studentExam: StudentExam): StudentExam {
        studentExam.exam = this.convertExamDateFromServer(studentExam.exam);
        return studentExam;
    }
}
