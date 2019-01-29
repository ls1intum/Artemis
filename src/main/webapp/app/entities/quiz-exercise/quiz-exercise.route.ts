import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Resolve, ActivatedRouteSnapshot, RouterStateSnapshot, Routes } from '@angular/router';
import { UserRouteAccessService } from '../../core';
import { of } from 'rxjs';
import { map } from 'rxjs/operators';

import { PendingChangesGuard } from '../../shared';

import { QuizExercise } from './quiz-exercise.model';
import { QuizExerciseService } from './quiz-exercise.service';
import { QuizExerciseComponent } from './quiz-exercise.component';
import { QuizExerciseDetailComponent } from './quiz-exercise-detail.component';
import { QuizExerciseExportComponent } from './quiz-exercise-export.component';
import { QuizExerciseDeletePopupComponent } from './quiz-exercise-delete-dialog.component';
import { QuizExerciseResetPopupComponent } from './quiz-exercise-reset-dialog.component';
import { QuizReEvaluateComponent } from '../../quiz/re-evaluate/quiz-re-evaluate.component';

@Injectable({ providedIn: 'root' })
export class QuizExerciseResolve implements Resolve<QuizExercise> {
    constructor(private service: QuizExerciseService) {}

    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
        const id = route.params['id'] ? route.params['id'] : null;
        if (id) {
            return this.service.find(id).pipe(map((quizExercise: HttpResponse<QuizExercise>) => quizExercise.body));
        }
        return of(new QuizExercise());
    }
}

export const quizExerciseRoute: Routes = [
    {
        path: 'quiz-exercise',
        component: QuizExerciseComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'arTeMiSApp.quizExercise.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'quiz-exercise/:id',
        component: QuizExerciseDetailComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'arTeMiSApp.quizExercise.home.title'
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [PendingChangesGuard]
    },
    {
        path: 'course/:courseId/quiz-exercise',
        component: QuizExerciseComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'arTeMiSApp.quizExercise.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'course/:courseId/quiz-exercise/re-evaluate/:id',
        component: QuizReEvaluateComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'arTeMiSApp.quizExercise.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'course/:courseId/quiz-exercise/edit/:id',
        component: QuizExerciseDetailComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'arTeMiSApp.quizExercise.home.title'
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [PendingChangesGuard]
    },
    {
        path: 'course/:courseId/quiz-exercise/new',
        component: QuizExerciseDetailComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'arTeMiSApp.quizExercise.home.title'
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [PendingChangesGuard]
    },
    {
        path: 'course/:courseId/quiz-exercise/export',
        component: QuizExerciseExportComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'arTeMiSApp.quizExercise.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const quizExercisePopupRoute: Routes = [
    {
        path: 'quiz-exercise/:id/delete',
        component: QuizExerciseDeletePopupComponent,
        resolve: {
            quizExercise: QuizExerciseResolve
        },
        data: {
            authorities: ['ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'arTeMiSApp.quizExercise.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'quiz-exercise/:id/reset',
        component: QuizExerciseResetPopupComponent,
        resolve: {
            quizExercise: QuizExerciseResolve
        },
        data: {
            authorities: ['ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'arTeMiSApp.quizExercise.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
