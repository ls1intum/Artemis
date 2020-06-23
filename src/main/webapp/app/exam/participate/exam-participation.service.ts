import { Injectable } from '@angular/core';
import { SERVER_API_URL } from 'app/app.constants';
import { Observable, pipe } from 'rxjs';
import { StudentExam } from 'app/entities/student-exam.model';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { LocalStorageService } from 'ngx-webstorage';
import { SessionStorageService } from 'ngx-webstorage';
import { QuizSubmission } from 'app/entities/quiz/quiz-submission.model';
import { tap } from 'rxjs/operators';

@Injectable({ providedIn: 'root' })
export class ExamParticipationService {
    constructor(private httpClient: HttpClient, private localStorageService: LocalStorageService, private sessionStorage: SessionStorageService) {}

    private getLocalStorageKeyForStudentExam(courseId: number, examId: number): string {
        const prefix = 'artemis_student_exam';
        return `${prefix}_${courseId}_${examId}`;
    }

    // TODO: we only take the local storage if the client is offline
    // /**
    //  * Retrieves a {@link StudentExam} from server or localstorge
    //  * @param courseId
    //  * @param examId
    //  */
    // public loadStudentExam(courseId: number, examId: number): Observable<StudentExam> {
    //     // check for localStorage
    //     const localStoredExam: StudentExam = JSON.parse(this.localStorageService.retrieve(this.getLocalStorageKeyForStudentExam(courseId, examId)));
    //     if (localStoredExam) {
    //         return Observable.of(localStoredExam);
    //     } else {
    //         // download student exam from server
    //         return this.getStudentExamFromServer(courseId, examId);
    //     }
    // }

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
    getStudentExamFromServer(courseId: number, examId: number): Observable<HttpResponse<StudentExam>> {
        const url = `${SERVER_API_URL}api/courses/${courseId}/exams/${examId}/studentExams/conduction`;
        return this.httpClient
            .get<StudentExam>(url, { observe: 'response' })
            .pipe(
                tap((res: HttpResponse<StudentExam>) => {
                    if (res.body) {
                        this.saveExamSessionTokenToSessionStorage(res.body.examSessions[0].sessionToken);
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
