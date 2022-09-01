import { Injectable } from '@angular/core';
import { GradingScale } from 'app/entities/grading-scale.model';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { GradeDTO, GradeStep, GradeStepsDTO } from 'app/entities/grade-step.model';
import { map } from 'rxjs/operators';
import { PageableSearch, SearchResult } from 'app/shared/table/pageable-table';
import { captureException } from '@sentry/angular';

export type EntityResponseType = HttpResponse<GradingScale>;
export type EntityArrayResponseType = HttpResponse<GradingScale[]>;

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
     * @param courseId the course for which the grade steps are retrieved
     */
    findGradeStepsForCourse(courseId: number): Observable<HttpResponse<GradeStepsDTO>> {
        return this.http.get<GradeStepsDTO>(`${this.resourceUrl}/${courseId}/grading-scale/grade-steps`, { observe: 'response' });
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
     * Finds all grade steps for a course or an exam
     *
     * @param courseId the course for which the grade steps are queried
     * @param examId if present the grade steps for this exam are queried instead
     */
    findGradeSteps(courseId: number, examId?: number): Observable<GradeStepsDTO | undefined> {
        let gradeStepsObservable: Observable<HttpResponse<GradeStepsDTO>>;
        if (examId != undefined) {
            gradeStepsObservable = this.findGradeStepsForExam(courseId, examId);
        } else {
            gradeStepsObservable = this.findGradeStepsForCourse(courseId);
        }
        return gradeStepsObservable.pipe(
            map((gradeStepsDTO) => {
                if (gradeStepsDTO && gradeStepsDTO.body) {
                    return gradeStepsDTO.body;
                }
            }),
        );
    }

    /**
     * TODO: Ata
     *
     */
    findWithBonusGradeTypeForInstructor(pageable: PageableSearch): Observable<HttpResponse<SearchResult<GradingScale>>> {
        const params = new HttpParams()
            .set('pageSize', String(pageable.pageSize))
            .set('page', String(pageable.page))
            .set('sortingOrder', pageable.sortingOrder)
            .set('searchTerm', pageable.searchTerm)
            .set('sortedColumn', pageable.sortedColumn);

        return this.http.get<SearchResult<GradingScale>>(`${SERVER_API_URL}api/grading-scales`, {
            params,
            observe: 'response',
        });
    }

    /**
     * Finds a grade step for course that matches the given percentage
     *
     * @param courseId the course to which the exam belongs
     * @param percentage the percentage which will be matched
     */
    public matchPercentageToGradeStepForCourse(courseId: number, percentage: number): Observable<HttpResponse<GradeDTO>> {
        return this.http.get<GradeDTO>(`${this.resourceUrl}/${courseId}/grading-scale/match-grade-step?gradePercentage=${percentage}`, { observe: 'response' });
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
     * Finds a grade step for an exam or a course that matches the given percentage
     *
     * @param percentage the percentage which will be matched
     * @param courseId the course for which the matching is done
     * @param examId if present, the matching is done for this exam instead
     */
    public matchPercentageToGradeStep(percentage: number, courseId: number, examId?: number): Observable<GradeDTO | undefined> {
        let responseObservable: Observable<HttpResponse<GradeDTO>>;
        if (examId != undefined) {
            responseObservable = this.matchPercentageToGradeStepForExam(courseId, examId, percentage);
        } else {
            responseObservable = this.matchPercentageToGradeStepForCourse(courseId, percentage);
        }
        return responseObservable.pipe(
            map((response) => {
                if (response && response.body) {
                    return response.body;
                }
            }),
        );
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
        const EPSILON = 0.01;
        if (Math.abs(percentage - gradeStep.lowerBoundPercentage) < EPSILON) {
            return gradeStep.lowerBoundInclusive;
        } else if (Math.abs(percentage - gradeStep.upperBoundPercentage) < EPSILON) {
            return gradeStep.upperBoundInclusive;
        } else {
            return percentage > gradeStep.lowerBoundPercentage && percentage < gradeStep.upperBoundPercentage;
        }
    }

    /**
     * Finds a matching grade step inside a grade step set for the given percentage
     * or returns the highest step if the score is above all steps.
     * Returns undefined if the score is below all grade steps
     *
     * @param gradeSteps the grade step set
     * @param percentage the percentage to be matched
     */
    findMatchingGradeStep(gradeSteps: GradeStep[], percentage: number) {
        const matchingGradeStep = gradeSteps.find((gradeStep) => {
            return this.matchGradePercentage(gradeStep, percentage);
        });

        if (matchingGradeStep) {
            return matchingGradeStep;
        }

        let sortedGradeSteps: GradeStep[] = [];
        gradeSteps.forEach((gradeStep) => sortedGradeSteps.push(Object.assign({}, gradeStep)));
        sortedGradeSteps = this.sortGradeSteps(sortedGradeSteps);

        return percentage > sortedGradeSteps.last()!.upperBoundPercentage ? sortedGradeSteps.last()! : undefined;
    }

    /**
     * Finds a matching grade step among the given grade steps by calculating percentage
     * from the given points and max points.
     * @see findMatchingGradeStep
     * @param gradeSteps
     * @param points
     * @param maxPoints
     */
    findMatchingGradeStepByPoints(gradeSteps: GradeStep[], points: number, maxPoints: number) {
        const percentage = (points / maxPoints) * 100;
        return this.findMatchingGradeStep(gradeSteps, percentage);
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

    /**
     * Sets the grade points
     *
     * @param gradeSteps the grade steps for which the grade points are set
     * @param maxPoints the max points, based on which the grade points are set
     */
    setGradePoints(gradeSteps: GradeStep[], maxPoints: number) {
        for (const gradeStep of gradeSteps) {
            gradeStep.lowerBoundPoints = (maxPoints * gradeStep.lowerBoundPercentage) / 100;
            gradeStep.upperBoundPoints = (maxPoints * gradeStep.upperBoundPercentage) / 100;
        }
    }

    /**
     * Determines whether all grade steps have their lower and upper bounds set in absolute points
     */
    hasPointsSet(gradeSteps: GradeStep[]): boolean {
        for (const gradeStep of gradeSteps) {
            if (gradeStep.lowerBoundPoints == undefined || gradeStep.upperBoundPoints == undefined || gradeStep.upperBoundPoints === 0) {
                return false;
            }
        }
        return gradeSteps.length !== 0;
    }

    /**
     * Gets the title of the course or exam related to the given grading scale.
     *
     * @param gradingScale a grading scale belonging to a course or an exam
     */
    getGradingScaleTitle(gradingScale: GradingScale): string | undefined {
        return gradingScale?.exam?.title ?? gradingScale?.course?.title;
    }

    /**
     * Gets the max points of the given grading scale from the related course or exam.
     *
     * @param gradingScale a grading scale belonging to a course or an exam
     */
    getGradingScaleMaxPoints(gradingScale?: GradingScale): number {
        return (gradingScale?.exam?.maxPoints ?? gradingScale?.course?.maxPoints) || 0;
    }

    /**
     * Parses the {@link gradeName} as a number in order to use it in grade and bonus calculations.
     * Accepts both "," and "." as decimal separators.
     *
     * Returns undefined on error.
     *
     * @param gradeName grade name of a grade step
     * @return number value corresponding to the {@link gradeName} or undefined if it is not parseable.
     */
    getNumericValueForGradeName(gradeName: string | undefined): number | undefined {
        if (gradeName == undefined) {
            return undefined;
        }
        gradeName = gradeName.replace(',', '.');
        const numericValue = parseFloat(gradeName);
        if (isNaN(numericValue)) {
            captureException(new Error(`Grade name: ${gradeName} cannot be parsed as float`));
            return undefined;
        }
        return numericValue;
    }
}
