import { HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { ActivatedRouteSnapshot, Resolve } from '@angular/router';
import { Observable, forkJoin, map } from 'rxjs';

import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { Course } from 'app/course/shared/entities/course.model';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { IrisAssessmentReviewHttpService } from 'app/iris/overview/ask-user/services/iris-assessment-review-http.service';
import { IrisAssessment } from 'app/iris/shared/entities/iris-assessment.model';
import { QAExchangeDTO } from 'app/iris/shared/entities/iris-qa-exchange-dto.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';

export interface IrisAssessmentReviewResolvedData {
    readonly course: Course;
    readonly exercise: ProgrammingExercise;
    readonly assessment: IrisAssessment;
    readonly rows: QAExchangeDTO[];
}

@Injectable({ providedIn: 'root' })
export class IrisAssessmentReviewResolver implements Resolve<IrisAssessmentReviewResolvedData> {
    private readonly courseService = inject(CourseManagementService);
    private readonly irisAssessmentReviewService = inject(IrisAssessmentReviewHttpService);

    resolve(route: ActivatedRouteSnapshot): Observable<IrisAssessmentReviewResolvedData> {
        const courseId = this.getRequiredId(route, 'courseId');
        const assessmentId = this.getRequiredId(route, 'assessmentId');
        const inClass = route.data['inClass'] as boolean;

        return forkJoin({
            courseResponse: this.courseService.find(courseId),
            assessmentResponse: this.irisAssessmentReviewService.findWithStudent(assessmentId),
            rowsResponse: this.irisAssessmentReviewService.getAssessmentChat(assessmentId, inClass),
        }).pipe(
            map(({ courseResponse, assessmentResponse, rowsResponse }) => {
                const course = this.requireBody(courseResponse, 'Course');
                const assessment = this.requireBody(assessmentResponse, 'Iris assessment');
                const exercise = assessment.exercise;
                const rows = this.requireBody(rowsResponse, 'Assessment chat');

                if (!exercise || exercise.type !== ExerciseType.PROGRAMMING) {
                    throw new Error(`Iris assessment ${assessmentId} is not linked to a programming exercise`);
                }

                return {
                    course,
                    exercise: exercise,
                    assessment,
                    rows,
                };
            }),
        );
    }

    private getRequiredId(route: ActivatedRouteSnapshot, parameterName: string): number {
        const rawValue = route.paramMap.get(parameterName);
        const id = Number(rawValue);

        if (!rawValue || !Number.isInteger(id) || id <= 0) {
            throw new Error(`Missing or invalid route parameter: ${parameterName}`);
        }

        return id;
    }

    private requireBody<T>(response: HttpResponse<T>, entityName: string): T {
        if (response.body === null) {
            throw new Error(`${entityName} could not be loaded`);
        }

        return response.body;
    }
}
