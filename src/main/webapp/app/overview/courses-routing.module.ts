import { RouterModule, Routes } from '@angular/router';
import { CoursesComponent } from 'app/overview/courses.component';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { CourseOverviewComponent } from 'app/overview/course-overview.component';
import { CourseExercisesComponent } from 'app/overview/course-exercises/course-exercises.component';
import { CourseLecturesComponent } from 'app/overview/course-lectures/course-lectures.component';
import { CourseExamsComponent } from 'app/overview/course-exams/course-exams.component';
import { CourseStatisticsComponent } from 'app/overview/course-statistics/course-statistics.component';
import { CourseExerciseDetailsComponent } from 'app/overview/exercise-details/course-exercise-details.component';
import { CourseLectureDetailsComponent } from 'app/overview/course-lectures/course-lecture-details.component';
import { TeamComponent } from 'app/exercises/shared/team/team.component';
import { NgModule } from '@angular/core';
import { Authority } from 'app/shared/constants/authority.constants';

const routes: Routes = [
    {
        path: 'courses',
        component: CoursesComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'overview.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'courses/:courseId',
        component: CourseOverviewComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'overview.course',
        },
        canActivate: [UserRouteAccessService],
        children: [
            {
                path: 'exercises',
                component: CourseExercisesComponent,
                data: {
                    authorities: [Authority.USER],
                    pageTitle: 'overview.course',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                path: 'lectures',
                component: CourseLecturesComponent,
                data: {
                    authorities: [Authority.USER],
                    pageTitle: 'overview.lectures',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                path: 'statistics',
                component: CourseStatisticsComponent,
                data: {
                    authorities: [Authority.USER],
                    pageTitle: 'overview.statistics',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                path: 'exams',
                component: CourseExamsComponent,
                data: {
                    authorities: [Authority.USER],
                    pageTitle: 'overview.exams',
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
        path: 'courses/:courseId/exercises/:exerciseId',
        component: CourseExerciseDetailsComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'overview.exercise',
        },
        canActivate: [UserRouteAccessService],
        children: [
            {
                path: '',
                pathMatch: 'full',
                loadChildren: () => import('app/overview/student-questions/student-questions.module').then((m) => m.ArtemisStudentQuestionsModule),
            },
        ],
    },
    {
        path: 'courses/:courseId/exercises/:exerciseId/teams/:teamId',
        component: TeamComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.team.detail.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'courses/:courseId/lectures/:lectureId',
        component: CourseLectureDetailsComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'overview.lectures',
        },
        canActivate: [UserRouteAccessService],
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule],
})
export class ArtemisCoursesRoutingModule {}
