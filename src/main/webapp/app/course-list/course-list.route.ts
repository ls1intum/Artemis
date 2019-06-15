import { Routes } from '@angular/router';

import { UserRouteAccessService } from '../core';
import { CourseListComponent } from './course-list.component';
import { CourseScoreCalculationComponent } from './course-score-calculation.component';

export const courseListRoute: Routes = [
    {
        path: 'courses',
        component: CourseListComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'artemisApp.course.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'courses/:courseId/exercise/:exerciseId',
        component: CourseListComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'artemisApp.course.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'course/:courseId/score-calculation',
        component: CourseScoreCalculationComponent,
        data: {
            authorities: ['ROLE_USER', 'ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.course.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
