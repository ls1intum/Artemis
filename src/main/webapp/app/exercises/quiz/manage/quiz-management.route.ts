import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRouteSnapshot, Resolve, RouterStateSnapshot, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Observable } from 'rxjs';
import { filter, map } from 'rxjs/operators';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { QuizExerciseService } from './quiz-exercise.service';
import { QuizExerciseComponent } from './quiz-exercise.component';
import { QuizExerciseDetailComponent } from './quiz-exercise-detail.component';
import { QuizExerciseExportComponent } from './quiz-exercise-export.component';
import { PendingChangesGuard } from 'app/shared/guard/pending-changes.guard';
import { QuizReEvaluateComponent } from 'app/exercises/quiz/manage/re-evaluate/quiz-re-evaluate.component';
import { CourseManagementService } from '../../../course/manage/course-management.service';
import { Course } from 'app/entities/course.model';

@Injectable({ providedIn: 'root' })
export class QuizExerciseResolve implements Resolve<QuizExercise> {
    constructor(private quizExerciseService: QuizExerciseService, private courseService: CourseManagementService) {}

    /**
     * Resolves the route and initializes quiz exercise either from exerciseId (existing exercise) or
     * from course id (new exercise)
     * @param route
     * @param state
     */
    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
        if (route.params['exerciseId']) {
            return this.quizExerciseService.find(route.params['exerciseId']).pipe(
                filter(res => !!res.body),
                map((quizExercise: HttpResponse<QuizExercise>) => quizExercise.body!),
            );
        } else if (route.params['courseId']) {
            return this.courseService.find(route.params['courseId']).pipe(
                filter(res => !!res.body),
                map((course: HttpResponse<Course>) => {
                    return new QuizExercise(course.body!);
                }),
            );
        }
        return Observable.of(new QuizExercise());
    }
}

export const quizManagementRoute: Routes = [
    {
        path: 'course-management/:courseId/quiz-exercises',
        component: QuizExerciseComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.quizExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'course-management/:courseId/quiz-exercises/:exerciseId',
        component: QuizExerciseDetailComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.quizExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [PendingChangesGuard],
    },
    {
        path: 'course-management/:courseId/quiz-exercises/:exerciseId/re-evaluate',
        component: QuizReEvaluateComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.quizExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'course-management/:courseId/quiz-exercises/:exerciseId/edit',
        component: QuizExerciseDetailComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.quizExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [PendingChangesGuard],
    },
    {
        path: 'course-management/:courseId/quiz-exercises/new',
        component: QuizExerciseDetailComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.quizExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [PendingChangesGuard],
    },
    {
        path: 'course-management/:courseId/quiz-exercises/export',
        component: QuizExerciseExportComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.quizExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
