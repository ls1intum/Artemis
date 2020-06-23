import { Injectable } from '@angular/core';
import { SERVER_API_URL } from 'app/app.constants';
import { Observable } from 'rxjs';
import { StudentExam } from 'app/entities/student-exam.model';
import { HttpClient } from '@angular/common/http';
import { LocalStorageService } from 'ngx-webstorage';
import { SessionStorageService } from 'ngx-webstorage';
import { QuizSubmission } from 'app/entities/quiz/quiz-submission.model';
import { StartExamResponse } from 'app/entities/start-exam-response.model';

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
        // check for localStorage
        const localStoredExam: StudentExam = JSON.parse(this.localStorageService.retrieve(this.getLocalStorageKeyForStudentExam(courseId, examId)));
        if (localStoredExam) {
            return Observable.of(localStoredExam);
        } else {
            // download student exam from server
            return this.getStudentExamFromServer(courseId, examId);
        }
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
    private getStudentExamFromServer(courseId: number, examId: number): Observable<StudentExam> | any {
        const url = `${SERVER_API_URL}api/courses/${courseId}/exams/${examId}/studentExams/conduction`;
        this.httpClient.get<StartExamResponse>(url).subscribe((startExamResponse) => {
            this.saveExamSessionTokenToSessionStorage(startExamResponse.examSession.sessionToken);
            return Observable.of(startExamResponse.studentExam);
        });
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
