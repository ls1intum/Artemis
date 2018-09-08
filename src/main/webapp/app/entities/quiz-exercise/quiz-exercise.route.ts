import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Resolve, ActivatedRouteSnapshot, RouterStateSnapshot, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core';
import { of } from 'rxjs';
import { map } from 'rxjs/operators';
import { QuizExercise } from 'app/shared/model/quiz-exercise.model';
import { QuizExerciseService } from './quiz-exercise.service';
import { QuizExerciseComponent } from './quiz-exercise.component';
import { QuizExerciseDetailComponent } from './quiz-exercise-detail.component';
import { QuizExerciseUpdateComponent } from './quiz-exercise-update.component';
import { QuizExerciseDeletePopupComponent } from './quiz-exercise-delete-dialog.component';
import { IQuizExercise } from 'app/shared/model/quiz-exercise.model';

@Injectable({ providedIn: 'root' })
export class QuizExerciseResolve implements Resolve<IQuizExercise> {
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
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.quizExercise.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'quiz-exercise/:id/view',
        component: QuizExerciseDetailComponent,
        resolve: {
            quizExercise: QuizExerciseResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.quizExercise.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'quiz-exercise/new',
        component: QuizExerciseUpdateComponent,
        resolve: {
            quizExercise: QuizExerciseResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.quizExercise.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'quiz-exercise/:id/edit',
        component: QuizExerciseUpdateComponent,
        resolve: {
            quizExercise: QuizExerciseResolve
        },
        data: {
            authorities: ['ROLE_USER'],
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
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.quizExercise.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
