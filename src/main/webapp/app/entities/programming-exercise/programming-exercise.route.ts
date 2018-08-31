import { Routes } from '@angular/router';

import { UserRouteAccessService } from '../../shared';
import { ProgrammingExerciseComponent } from './programming-exercise.component';
import { ProgrammingExerciseDetailComponent } from './programming-exercise-detail.component';
import { ProgrammingExercisePopupComponent } from './programming-exercise-dialog.component';
import { ProgrammingExerciseDeletePopupComponent } from './programming-exercise-delete-dialog.component';

export const programmingExerciseRoute: Routes = [
    {
        path: 'programming-exercise',
        component: ProgrammingExerciseComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'arTeMiSApp.programmingExercise.home.title'
        },
        canActivate: [UserRouteAccessService]
    }, {
        path: 'programming-exercise/:id',
        component: ProgrammingExerciseDetailComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'arTeMiSApp.programmingExercise.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'course/:courseId/programming-exercise',
        component: ProgrammingExerciseComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'arTeMiSApp.programmingExercise.home.title'
        },
        canActivate: [UserRouteAccessService]
    }, {
        path: 'course/:courseId/programming-exercise/:id',
        component: ProgrammingExerciseDetailComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'arTeMiSApp.programmingExercise.home.title'
        },
        canActivate: [UserRouteAccessService]
    }

];

export const programmingExercisePopupRoute: Routes = [
    {
        path: 'course/:courseId/programming-exercise-new',
        component: ProgrammingExercisePopupComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'arTeMiSApp.programmingExercise.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'programming-exercise/:id/edit',
        component: ProgrammingExercisePopupComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'arTeMiSApp.programmingExercise.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'programming-exercise/:id/delete',
        component: ProgrammingExerciseDeletePopupComponent,
        data: {
            authorities: ['ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'arTeMiSApp.programmingExercise.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
