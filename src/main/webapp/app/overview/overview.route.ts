import { Routes } from '@angular/router';

import {
    CourseExercisesComponent,
    CourseOverviewComponent,
    CourseGradebookComponent,
    CourseStatisticsComponent,
    OverviewComponent, CourseExerciseDetailsComponent
} from './';
import { UserRouteAccessService } from 'app/core';

export const OVERVIEW_ROUTES: Routes = [
    {
        path: 'overview',
        component: OverviewComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'overview.title'
        },
        canActivate: [UserRouteAccessService]
    }, {
        path: 'overview/:courseId',
        component: CourseOverviewComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'overview.course'
        },
        canActivate: [UserRouteAccessService],
        children: [
            {
                path: 'exercises',
                component: CourseExercisesComponent,
                data: {
                    authorities: ['ROLE_USER'],
                    pageTitle: 'overview.course'
                },
                canActivate: [UserRouteAccessService],
            }, {
                path: 'statistics',
                component: CourseStatisticsComponent,
                data: {
                    authorities: ['ROLE_USER'],
                    pageTitle: 'overview.statistics'
                },
                canActivate: [UserRouteAccessService],
            }, {
                path: 'gradebook',
                component: CourseGradebookComponent,
                data: {
                    authorities: ['ROLE_USER'],
                    pageTitle: 'overview.gradebook'
                },
                canActivate: [UserRouteAccessService],
            }, {
                path: '',
                redirectTo: 'exercises',
                pathMatch: 'full'
            }
        ]
    }, {
        path: 'overview/:courseId/exercises/:exerciseId',
        component: CourseExerciseDetailsComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'overview.course'
        },
        canActivate: [UserRouteAccessService],
    }
];
