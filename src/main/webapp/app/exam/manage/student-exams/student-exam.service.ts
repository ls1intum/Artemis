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
    public resourceUrlStudentExam = SERVER_API_URL + 'api/studentExams';
    public resourceUrlExams = SERVER_API_URL + 'api/exams';

    constructor(private router: Router, private http: HttpClient) {}

    /**
     * Find a student exam on the server using a GET request.
     * @param id The id of the student exam to get.
     */
    find(id: number): Observable<EntityResponseType> {
        return this.http.get<StudentExam>(`${this.resourceUrlStudentExam}/${id}`, { observe: 'response' });
    }

    /**
     * Find all student exams for the given exam.
     * @param examId The exam id.
     */
    findAllForExam(examId: number): Observable<EntityArrayResponseType> {
        return this.http.get<StudentExam[]>(`${this.resourceUrlExams}/${examId}/studentExams`, { observe: 'response' });
    }
}
