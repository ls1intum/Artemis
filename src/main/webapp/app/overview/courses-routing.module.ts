import { RouterModule, Routes } from '@angular/router';
import { CoursesComponent } from 'app/overview/courses.component';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { CourseOverviewComponent } from 'app/overview/course-overview.component';
import { CourseLecturesComponent } from 'app/overview/course-lectures/course-lectures.component';
import { CourseExamsComponent } from 'app/overview/course-exams/course-exams.component';
import { NgModule } from '@angular/core';
import { Authority } from 'app/shared/constants/authority.constants';
import { CourseExercisesComponent } from 'app/overview/course-exercises/course-exercises.component';
import { GradingKeyOverviewComponent } from 'app/grading-system/grading-key-overview/grading-key-overview.component';

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
        path: 'courses/register',
        loadChildren: () => import('./course-registration/course-registration.module').then((m) => m.CourseRegistrationModule),
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
                loadChildren: () => import('./course-statistics/course-statistics.module').then((m) => m.CourseStatisticsModule),
            },
            {
                path: 'learning-goals',
                loadChildren: () => import('../overview/course-learning-goals/course-learning-goals.module').then((m) => m.CourseLearningGoalsModule),
            },
            {
                path: 'discussion',
                loadChildren: () => import('../overview/course-discussion/course-discussion.module').then((m) => m.CourseDiscussionModule),
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
                path: 'plagiarism-cases',
                loadChildren: () => import('../course/plagiarism-cases/student-view/plagiarism-cases-student-view.module').then((m) => m.ArtemisPlagiarismCasesStudentViewModule),
            },
            {
                path: '',
                redirectTo: 'exercises',
                pathMatch: 'full',
            },
        ],
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule],
})
export class ArtemisCoursesRoutingModule {}
