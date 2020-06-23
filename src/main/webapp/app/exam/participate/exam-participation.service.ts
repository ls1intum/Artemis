import { Injectable } from '@angular/core';
import { SERVER_API_URL } from 'app/app.constants';
import { Observable, from } from 'rxjs';
import { StudentExam } from 'app/entities/student-exam.model';
import { HttpClient } from '@angular/common/http';
import { LocalStorageService } from 'ngx-webstorage';
import { SessionStorageService } from 'ngx-webstorage';
import { QuizSubmission } from 'app/entities/quiz/quiz-submission.model';
import { catchError, tap } from 'rxjs/operators';

@Injectable({ providedIn: 'root' })
export class ExamParticipationService {
    constructor(private httpClient: HttpClient, private localStorageService: LocalStorageService, private sessionStorage: SessionStorageService) {}

    private getLocalStorageKeyForStudentExam(courseId: number, examId: number): string {
        const prefix = 'artemis_student_exam';
        return `${prefix}_${courseId}_${examId}`;
    }

    /**
     * Retrieves a {@link StudentExam} from server or localstorge
     * @param courseId
     * @param examId
     */
    public loadStudentExam(courseId: number, examId: number): Observable<StudentExam> {
        // download student exam from server
        return this.getStudentExamFromServer(courseId, examId).pipe(
            catchError(() => {
                const localStoredExam: StudentExam = JSON.parse(this.localStorageService.retrieve(this.getLocalStorageKeyForStudentExam(courseId, examId)));
                return Observable.of(localStoredExam);
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
     * save the examSession to the session Storage
     *
     * @param courseId
     * @param examId
     * @param studentExam
     */
    public saveExamSessionTokenToSessionStorage(examSessionToken: string): void {
        this.sessionStorage.store('ExamSessionToken', examSessionToken);
    }

    /**
     * Retrieves a {@link StudentExam} from server
     */
    private getStudentExamFromServer(courseId: number, examId: number): Observable<StudentExam> {
        const url = `${SERVER_API_URL}api/courses/${courseId}/exams/${examId}/studentExams/conduction`;
        // TODO: convert Date from server
        return this.httpClient.get<StudentExam>(url).pipe(
            tap((studentExam: StudentExam) => {
                if (studentExam.examSessions) {
                    this.saveExamSessionTokenToSessionStorage(studentExam.examSessions[0].sessionToken);
                }
            }),
        );
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
}
