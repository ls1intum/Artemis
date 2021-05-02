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

    /**
     * Store a new grading scale for course on the server
     *
     * @param courseId the course for which the grading scale will be created
     * @param gradingScale the grading scale to be created
     */
    createGradingScaleForCourse(courseId: number, gradingScale: GradingScale): Observable<EntityResponseType> {
        return this.http.post<GradingScale>(`${this.resourceUrl}/${courseId}/grading-scale`, gradingScale, { observe: 'response' });
    }

    /**
     * Update a grading scale for course on the server
     *
     * @param courseId the course for which the grading scale will be updated
     * @param gradingScale the grading scale to be updated
     */
    updateGradingScaleForCourse(courseId: number, gradingScale: GradingScale): Observable<EntityResponseType> {
        return this.http.put<GradingScale>(`${this.resourceUrl}/${courseId}/grading-scale`, gradingScale, { observe: 'response' });
    }

    /**
     * Retrieves the grading scale for course
     *
     * @param courseId the course for which the grading scale will be retrieved
     */
    findGradingScaleForCourse(courseId: number): Observable<EntityResponseType> {
        return this.http.get<GradingScale>(`${this.resourceUrl}/${courseId}/grading-scale`, { observe: 'response' });
    }

    /**
     * Deletes the grading scale for course
     *
     * @param courseId the course for which the grading scale will be deleted
     */
    deleteGradingScaleForCourse(courseId: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${courseId}/grading-scale`, { observe: 'response' });
    }

    /**
     * Store a new grading scale for exam on the server
     *
     * @param courseId the course to which the exam belongs
     * @param examId the exam for which the grading scale will be created
     * @param gradingScale the grading scale to be created
     */
    createGradingScaleForExam(courseId: number, examId: number, gradingScale: GradingScale): Observable<EntityResponseType> {
        return this.http.post<GradingScale>(`${this.resourceUrl}/${courseId}/exams/${examId}/grading-scale`, gradingScale, { observe: 'response' });
    }

    /**
     * Update a grading scale for exam on the server
     *
     * @param courseId the course to which the exam belongs
     * @param examId the exam for which the grading scale will be updated
     * @param gradingScale the grading scale to be updated
     */
    updateGradingScaleForExam(courseId: number, examId: number, gradingScale: GradingScale): Observable<EntityResponseType> {
        return this.http.put<GradingScale>(`${this.resourceUrl}/${courseId}/exams/${examId}/grading-scale`, gradingScale, { observe: 'response' });
    }

    /**
     * Retrieves the grading scale for exam
     *
     * @param courseId the course to which the exam belongs
     * @param examId the exam for which the grading scale will be retrieved
     */
    findGradingScaleForExam(courseId: number, examId: number): Observable<EntityResponseType> {
        return this.http.get<GradingScale>(`${this.resourceUrl}/${courseId}/exams/${examId}/grading-scale`, { observe: 'response' });
    }

    /**
     * Deletes the grading scale for exam
     *
     * @param courseId the course to which the exam belongs
     * @param examId the exam for which the grading scale will be deleted
     */
    deleteGradingScaleForExam(courseId: number, examId: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${courseId}/exams/${examId}/grading-scale`, { observe: 'response' });
    }
}
