import { RouterModule, Routes } from '@angular/router';
import { CoursesComponent } from 'app/overview/courses.component';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { CourseOverviewComponent } from 'app/overview/course-overview.component';
import { CourseLecturesComponent } from 'app/overview/course-lectures/course-lectures.component';
import { CourseExamsComponent } from 'app/overview/course-exams/course-exams.component';
import { NgModule } from '@angular/core';
import { Authority } from 'app/shared/constants/authority.constants';
import { CourseExercisesComponent } from 'app/overview/course-exercises/course-exercises.component';

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
        path: 'courses/enroll',
        loadChildren: () => import('./course-registration/course-registration.module').then((m) => m.CourseRegistrationModule),
    },
    // /courses/:courseId/register is special,
    // because we won't have access to the course object before the user is registered,
    // so we need to load it outside the normal course routing
    {
        path: 'courses/:courseId/register',
        loadChildren: () => import('./course-registration/course-registration-detail/course-registration-detail.module').then((m) => m.CourseRegistrationDetailModule),
    },
    // General course subpages for course-registered users
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
                path: 'competencies',
                loadChildren: () => import('./course-competencies/course-competencies.module').then((m) => m.CourseCompetenciesModule),
            },
            {
                path: 'discussion',
                loadChildren: () => import('./course-discussion/course-discussion.module').then((m) => m.CourseDiscussionModule),
            },
            {
                path: 'messages',
                loadChildren: () => import('./course-conversations/course-conversations.module').then((m) => m.CourseConversationsModule),
            },
            {
                path: 'tutorial-groups',
                loadChildren: () => import('./course-tutorial-groups/course-tutorial-groups.module').then((m) => m.CourseTutorialGroupsModule),
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
