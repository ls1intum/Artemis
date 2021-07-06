import { Injectable } from '@angular/core';
import { GradingScale } from 'app/entities/grading-scale.model';
import { SERVER_API_URL } from 'app/app.constants';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { GradeDTO, GradeStep, GradeStepsDTO } from 'app/entities/grade-step.model';

export type EntityResponseType = HttpResponse<GradingScale>;

@Injectable({ providedIn: 'root' })
export class GradingSystemService {
    public resourceUrl = SERVER_API_URL + 'api/courses';

    constructor(private http: HttpClient) {}

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

    /**
     * Finds all grade steps for exam
     *
     * @param courseId the course to which the exam belongs
     * @param examId the exam for which the grade steps are retrieved
     */
    findGradeStepsForExam(courseId: number, examId: number): Observable<HttpResponse<GradeStepsDTO>> {
        return this.http.get<GradeStepsDTO>(`${this.resourceUrl}/${courseId}/exams/${examId}/grading-scale/grade-steps`, { observe: 'response' });
    }

    /**
     * Finds a grade step for exam that matches the given percentage
     *
     * @param courseId the course to which the exam belongs
     * @param examId the exam for which the grade step is retrieved
     * @param percentage the percentage which will be matched
     */
    public matchPercentageToGradeStepForExam(courseId: number, examId: number, percentage: number): Observable<HttpResponse<GradeDTO>> {
        return this.http.get<GradeDTO>(`${this.resourceUrl}/${courseId}/exams/${examId}/grading-scale/match-grade-step?gradePercentage=${percentage}`, { observe: 'response' });
    }

    /**
     * Sorts grade steps by lower bound percentage
     *
     * @param gradeSteps the grade steps to be sorted
     */
    sortGradeSteps(gradeSteps: GradeStep[]): GradeStep[] {
        return gradeSteps.sort((gradeStep1, gradeStep2) => {
            return gradeStep1.lowerBoundPercentage - gradeStep2.lowerBoundPercentage;
        });
    }

    /**
     * Determines whether a given percentage matches the corresponding grade step
     *
     * @param gradeStep the grade step
     * @param percentage the percentage to be matched
     */
    matchGradePercentage(gradeStep: GradeStep, percentage: number): boolean {
        if (Math.abs(percentage - gradeStep.lowerBoundPercentage) < Number.EPSILON) {
            return gradeStep.lowerBoundInclusive;
        } else if (Math.abs(percentage - gradeStep.upperBoundPercentage) < Number.EPSILON) {
            return gradeStep.upperBoundInclusive;
        } else {
            return percentage > gradeStep.lowerBoundPercentage && percentage < gradeStep.upperBoundPercentage;
        }
    }

    /**
     * Finds a matching grade step inside a grade step set for the given percentage or returns undefined
     *
     * @param gradeSteps the grade step set
     * @param percentage the percentage to be matched
     */
    findMatchingGradeStep(gradeSteps: GradeStep[], percentage: number) {
        return gradeSteps.find((gradeStep) => {
            return this.matchGradePercentage(gradeStep, percentage);
        });
    }

    /**
     * Returns the max grade from a given grade step set
     *
     * @param gradeSteps the grade step set
     */
    maxGrade(gradeSteps: GradeStep[]): string {
        const maxGradeStep = gradeSteps.find((gradeStep) => {
            return gradeStep.upperBoundInclusive && gradeStep.upperBoundPercentage === 100;
        });
        return maxGradeStep?.gradeName || '';
    }

    setGradePoints(gradeSteps: GradeStep[], maxPoints: number) {
        for (const gradeStep of gradeSteps) {
            gradeStep.lowerBoundPoints = (maxPoints * gradeStep.lowerBoundPercentage) / 100;
            gradeStep.upperBoundPoints = (maxPoints * gradeStep.upperBoundPercentage) / 100;
        }
    }
}
