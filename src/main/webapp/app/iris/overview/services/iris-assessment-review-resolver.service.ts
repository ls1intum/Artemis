import { HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { ActivatedRouteSnapshot, Resolve } from '@angular/router';
import { Observable, forkJoin, map } from 'rxjs';

import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { IrisAssessmentReviewService } from 'app/iris/overview/services/iris-assessment-review.service';
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
    private readonly exerciseService = inject(ExerciseService);
    private readonly irisAssessmentReviewService = inject(IrisAssessmentReviewService);

    resolve(route: ActivatedRouteSnapshot): Observable<IrisAssessmentReviewResolvedData> {
        const courseId = this.getRequiredId(route, 'courseId');
        const exerciseId = this.getRequiredId(route, 'exerciseId');
        const assessmentId = this.getRequiredId(route, 'assessmentId');

        return forkJoin({
            courseResponse: this.courseService.find(courseId),
            exerciseResponse: this.exerciseService.find(exerciseId),
            assessmentResponse: this.irisAssessmentReviewService.findWithPoints(assessmentId),
            rowsResponse: this.irisAssessmentReviewService.getAssessmentChat(assessmentId),
        }).pipe(
            map(({ courseResponse, exerciseResponse, assessmentResponse, rowsResponse }) => {
                const course = this.requireBody(courseResponse, 'Course');
                const exercise = this.requireBody(exerciseResponse, 'Exercise');
                const assessment = this.requireBody(assessmentResponse, 'Iris assessment');
                const rows = this.requireBody(rowsResponse, 'Assessment chat');

                if (exercise.type !== ExerciseType.PROGRAMMING) {
                    throw new Error(`Exercise ${exerciseId} is not a programming exercise`);
                }

                return {
                    course,
                    exercise: exercise as ProgrammingExercise,
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
