import { Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';

import { SERVER_API_URL } from 'app/app.constants';
import { StudentExam } from 'app/entities/student-exam.model';

type EntityResponseType = HttpResponse<StudentExam>;

@Injectable({ providedIn: 'root' })
export class ExamParticipationService {
    public resourceUrl = SERVER_API_URL + 'api/courses';

    constructor(private router: Router, private http: HttpClient) {}

    /**
     * Update an StudentExam on the server using a PUT request.
     * @param courseId The course id.
     * @param {StudentExam} studentExam The StudentExam to update.
     */
    update(courseId: number, examId: number, studentExam: StudentExam): Observable<EntityResponseType> {
        return this.http.put<StudentExam>(`${this.resourceUrl}/${courseId}/exams/${examId}/studentExams/${studentExam.id}`, studentExam, { observe: 'response' });
    }
}
