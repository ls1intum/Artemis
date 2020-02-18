import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { CourseLectureDetailsComponent } from 'app/overview/course-lectures/course-lecture-details.component';
import { CourseExercisesComponent } from 'app/overview/course-exercises/course-exercises.component';
import { OverviewComponent } from 'app/overview/overview.component';
import { CourseStatisticsComponent } from 'app/overview/course-statistics/course-statistics.component';
import { CourseLecturesComponent } from 'app/overview/course-lectures/course-lectures.component';
import { CourseOverviewComponent } from 'app/overview/course-overview.component';
import { CourseExerciseDetailsComponent } from 'app/overview/exercise-details/course-exercise-details.component';

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
