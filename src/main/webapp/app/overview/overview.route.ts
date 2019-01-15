import { Routes } from '@angular/router';

import {
    CourseExercisesComponent,
    CourseOverviewComponent,
    CourseResultsComponent,
    CourseStatisticsComponent,
    OverviewComponent
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
                path: 'results',
                component: CourseResultsComponent,
                data: {
                    authorities: ['ROLE_USER'],
                    pageTitle: 'overview.results'
                },
                canActivate: [UserRouteAccessService],
            }, {
                path: '',
                redirectTo: 'exercises',
                pathMatch: 'full'
            }
        ]
    }
];
