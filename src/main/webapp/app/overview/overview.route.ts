import { Routes } from '@angular/router';

import {
    CourseExerciseDetailsComponent,
    CourseExercisesComponent,
    CourseLectureDetailsComponent,
    CourseLecturesComponent,
    CourseOverviewComponent,
    CourseStatisticsComponent,
    OverviewComponent,
} from './';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

export const OVERVIEW_ROUTES: Routes = [
    {
        path: 'overview',
        component: OverviewComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'overview.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'overview/:courseId',
        component: CourseOverviewComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'overview.course',
        },
        canActivate: [UserRouteAccessService],
        children: [
            {
                path: 'exercises',
                component: CourseExercisesComponent,
                data: {
                    authorities: ['ROLE_USER'],
                    pageTitle: 'overview.course',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                path: 'lectures',
                component: CourseLecturesComponent,
                data: {
                    authorities: ['ROLE_USER'],
                    pageTitle: 'overview.lectures',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                path: 'statistics',
                component: CourseStatisticsComponent,
                data: {
                    authorities: ['ROLE_USER'],
                    pageTitle: 'overview.statistics',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                path: '',
                redirectTo: 'exercises',
                pathMatch: 'full',
            },
        ],
    },
    {
        path: 'overview/:courseId/exercises/:exerciseId',
        component: CourseExerciseDetailsComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'overview.exercise',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'overview/:courseId/lectures/:lectureId',
        component: CourseLectureDetailsComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'overview.lectures',
        },
        canActivate: [UserRouteAccessService],
    },
];
