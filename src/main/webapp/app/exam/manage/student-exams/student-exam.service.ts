import { Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { StudentExam } from 'app/entities/student-exam.model';

type EntityResponseType = HttpResponse<StudentExam>;
type EntityArrayResponseType = HttpResponse<StudentExam[]>;

@Injectable({ providedIn: 'root' })
export class StudentExamService {
    public resourceUrl = SERVER_API_URL + 'api/courses';

    constructor(private router: Router, private http: HttpClient) {}

    /* istanbul ignore next */
    /**
     * Find a student exam on the server using a GET request.
     * @param courseId The course id.
     * @param examId The exam id.
     * @param studentExamId The id of the student exam to get.
     */
    find(courseId: number, examId: number, studentExamId: number): Observable<EntityResponseType> {
        return this.http.get<StudentExam>(`${this.resourceUrl}/${courseId}/exams/${examId}/student-exams/${studentExamId}`, { observe: 'response' });
    }

    /* istanbul ignore next */
    /**
     * Find all student exams for the given exam.
     * @param courseId The course id.
     * @param examId The exam id.
     */
    findAllForExam(courseId: number, examId: number): Observable<EntityArrayResponseType> {
        return this.http.get<StudentExam[]>(`${this.resourceUrl}/${courseId}/exams/${examId}/student-exams`, { observe: 'response' });
    }

    /* istanbul ignore next */
    /**
     * Update the working time of the given student exam.
     * @param courseId The course id.
     * @param examId The exam id.
     * @param studentExamId The id of the student exam to get.
     * @param workingTime The working time in seconds.
     */
    updateWorkingTime(courseId: number, examId: number, studentExamId: number, workingTime: number): Observable<EntityResponseType> {
        return this.http.patch<StudentExam>(`${this.resourceUrl}/${courseId}/exams/${examId}/student-exams/${studentExamId}/working-time`, workingTime, { observe: 'response' });
    }

    toggleSubmittedState(courseId: number, examId: number, studentExamId: number, unsubmit: boolean): Observable<EntityResponseType> {
        const url = `${this.resourceUrl}/${courseId}/exams/${examId}/student-exams/${studentExamId}/toggle-to-`;
        if (unsubmit) {
            return this.http.put<StudentExam>(url + `unsubmitted`, {}, { observe: 'response' });
        } else {
            return this.http.put<StudentExam>(url + `submitted`, {}, { observe: 'response' });
        }
    }
}
