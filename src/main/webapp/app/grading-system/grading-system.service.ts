import { Injectable } from '@angular/core';
import { GradingScale } from 'app/entities/grading-scale.model';
import { SERVER_API_URL } from 'app/app.constants';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable } from 'rxjs';

export type EntityResponseType = HttpResponse<GradingScale>;

@Injectable({ providedIn: 'root' })
export class GradingSystemService {
    public resourceUrl = SERVER_API_URL + 'api/courses';

    constructor(private router: Router, private http: HttpClient) {}

    createGradingScaleForCourse(courseId: number, gradingScale: GradingScale): Observable<EntityResponseType> {
        return this.http.post<GradingScale>(`${this.resourceUrl}/${courseId}/grading-scale`, gradingScale, { observe: 'response' });
    }

    updateGradingScaleForCourse(courseId: number, gradingScale: GradingScale): Observable<EntityResponseType> {
        return this.http.put<GradingScale>(`${this.resourceUrl}/${courseId}/grading-scale`, gradingScale, { observe: 'response' });
    }

    findGradingScaleForCourse(courseId: number): Observable<EntityResponseType> {
        return this.http.get<GradingScale>(`${this.resourceUrl}/${courseId}/grading-scale`, { observe: 'response' });
    }

    deleteGradingScaleForCourse(courseId: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${courseId}/grading-scale`, { observe: 'response' });
    }

    createGradingScaleForExam(courseId: number, examId: number, gradingScale: GradingScale): Observable<EntityResponseType> {
        return this.http.post<GradingScale>(`${this.resourceUrl}/${courseId}/exams/${examId}/grading-scale`, gradingScale, { observe: 'response' });
    }

    updateGradingScaleForExam(courseId: number, examId: number, gradingScale: GradingScale): Observable<EntityResponseType> {
        return this.http.put<GradingScale>(`${this.resourceUrl}/${courseId}/exams/${examId}/grading-scale`, gradingScale, { observe: 'response' });
    }

    findGradingScaleForExam(courseId: number, examId: number): Observable<EntityResponseType> {
        return this.http.get<GradingScale>(`${this.resourceUrl}/${courseId}/exams/${examId}/grading-scale`, { observe: 'response' });
    }

    deleteGradingScaleForExam(courseId: number, examId: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${courseId}/exams/${examId}/grading-scale`, { observe: 'response' });
    }
}
