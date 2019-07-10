import { Routes } from '@angular/router';

import { UserRouteAccessService } from '../core';
import { ExampleModelingSolutionComponent } from './example-modeling-solution.component';

export const exampleModelingSolutionRoute: Routes = [
    {
        path: 'modeling-exercise/:exerciseId/example-solution',
        component: ExampleModelingSolutionComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'artemisApp.modelingExercise.exampleSolution',
        },
        canActivate: [UserRouteAccessService],
    },
];
