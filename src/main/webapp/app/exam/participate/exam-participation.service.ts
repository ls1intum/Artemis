import { Injectable } from '@angular/core';
import { SERVER_API_URL } from 'app/app.constants';
import { Observable } from 'rxjs';
import { StudentExam } from 'app/entities/student-exam.model';
import { HttpClient } from '@angular/common/http';
import { LocalStorageService } from 'ngx-webstorage';
import { SessionStorageService } from 'ngx-webstorage';
import { QuizSubmission } from 'app/entities/quiz/quiz-submission.model';
import { catchError, map } from 'rxjs/operators';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { Exam } from 'app/entities/exam.model';
import * as moment from 'moment';

@Injectable({ providedIn: 'root' })
export class ExamParticipationService {
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
     * Retrieves a {@link StudentExam} from server or localstorge
     * @param courseId the id of the course the exam is created in
     * @param examId the id of the exam
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
     * Loads {@link Exam} object from server
     * @param courseId the id of the course the exam is created in
     * @param examId the id of the exam
     */
    public loadExam(courseId: number, examId: number): Observable<Exam> {
        const url = this.getResourceURL(courseId, examId) + '/conduction';
        return this.httpClient.get<Exam>(url).map((exam: Exam) => this.convertExamDateFromServer(exam));
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
     * Retrieves a {@link StudentExam} from server
     */
    private getStudentExamFromServer(courseId: number, examId: number): Observable<StudentExam> {
        const url = this.getResourceURL(courseId, examId) + '/studentExams/conduction';
        return this.httpClient.get<StudentExam>(url).pipe(
            map((studentExam: StudentExam) => {
                if (studentExam.examSessions) {
                    this.saveExamSessionTokenToSessionStorage(studentExam.examSessions[0].sessionToken);
                }

                return this.convertStudentExamFromServer(studentExam);
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

    private convertStudentExamFromServer(studentExam: StudentExam): StudentExam {
        studentExam.exercises = this.exerciseService.convertExercisesDateFromServer(studentExam.exercises);
        studentExam.exam = this.convertExamDateFromServer(studentExam.exam);
        return studentExam;
    }

    private convertExamDateFromServer(exam: Exam): Exam {
        exam.visibleDate = exam.visibleDate ? moment(exam.visibleDate) : null;
        exam.startDate = exam.startDate ? moment(exam.startDate) : null;
        exam.endDate = exam.endDate ? moment(exam.endDate) : null;
        return exam;
    }
}
