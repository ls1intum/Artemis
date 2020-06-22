import { Injectable } from '@angular/core';
import { SERVER_API_URL } from 'app/app.constants';
import { Observable } from 'rxjs';
import { StudentExam } from 'app/entities/student-exam.model';
import { HttpClient } from '@angular/common/http';
import { LocalStorageService } from 'ngx-webstorage';

@Injectable({ providedIn: 'root' })
export class ExamParticipationService {
    constructor(private httpClient: HttpClient, private localStorageService: LocalStorageService) {}

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
     * @param courseId
     * @param examId
     * @param studentExam
     */
    public saveStudentExamToLocalStorage(courseId: number, examId: number, studentExam: StudentExam): void {
        this.localStorageService.store(this.getLocalStorageKeyForStudentExam(courseId, examId), studentExam);
    }

    /**
     * Retrieves a {@link StudentExam} from server
     */
    private getStudentExamFromServer(courseId: number, examId: number): Observable<StudentExam> {
        const url = `${SERVER_API_URL}api/courses/${courseId}/exams/${examId}/studentExams/conduction`;
        return this.httpClient.get<StudentExam>(url);
    }
}
