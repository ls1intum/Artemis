import { Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { SERVER_API_URL } from 'app/app.constants';
import { StudentExam } from 'app/entities/student-exam.model';

type EntityResponseType = HttpResponse<StudentExam>;
type EntityArrayResponseType = HttpResponse<StudentExam[]>;

@Injectable({ providedIn: 'root' })
export class StudentExamService {
    public resourceUrl = SERVER_API_URL + 'api/courses';

    constructor(private router: Router, private http: HttpClient) {}

    /**
     * Find a student exam on the server using a GET request.
     * @param courseId The course id.
     * @param examId The exam id.
     * @param studentExamId The id of the student exam to get.
     */
    find(courseId: number, examId: number, studentExamId: number): Observable<EntityResponseType> {
        return this.http.get<StudentExam>(`${this.resourceUrl}/${courseId}/exams/${examId}/studentExams/${studentExamId}`, { observe: 'response' });
    }

    /**
     * Find all student exams for the given exam.
     * @param courseId The course id.
     * @param examId The exam id.
     */
    findAllForExam(courseId: number, examId: number): Observable<EntityArrayResponseType> {
        return this.http.get<StudentExam[]>(`${this.resourceUrl}/${courseId}/exams/${examId}/studentExams`, { observe: 'response' });
    }
}
