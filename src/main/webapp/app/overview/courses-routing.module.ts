import { RouterModule, Routes } from '@angular/router';
import { CoursesComponent } from 'app/overview/courses.component';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { CourseLecturesComponent } from 'app/overview/course-lectures/course-lectures.component';
import { NgModule } from '@angular/core';
import { Authority } from 'app/shared/constants/authority.constants';
import { CourseExercisesComponent } from 'app/overview/course-exercises/course-exercises.component';
import { CourseOverviewComponent } from './course-overview.component';

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
                    pageTitle: 'overview.exercises',
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
                data: {
                    authorities: [Authority.USER],
                    pageTitle: 'overview.statistics',
                },
            },
            {
                path: 'competencies',
                loadChildren: () => import('./course-competencies/course-competencies.module').then((m) => m.CourseCompetenciesModule),
                data: {
                    authorities: [Authority.USER],
                    pageTitle: 'overview.competencies',
                },
            },
            {
                path: 'learning-path',
                loadChildren: () => import('app/course/learning-paths/learning-paths.module').then((m) => m.ArtemisLearningPathsModule),
                data: {
                    authorities: [Authority.USER],
                    pageTitle: 'overview.learningPath',
                },
            },
            {
                path: 'discussion',
                loadChildren: () => import('./course-discussion/course-discussion.module').then((m) => m.CourseDiscussionModule),
                data: {
                    authorities: [Authority.USER],
                    pageTitle: 'overview.communication',
                },
            },
            {
                path: 'messages',
                loadChildren: () => import('./course-conversations/course-conversations.module').then((m) => m.CourseConversationsModule),
                data: {
                    authorities: [Authority.USER],
                    pageTitle: 'overview.messages',
                },
            },
            {
                path: 'tutorial-groups',
                loadChildren: () => import('./course-tutorial-groups/course-tutorial-groups.module').then((m) => m.CourseTutorialGroupsModule),
                data: {
                    authorities: [Authority.USER],
                    pageTitle: 'overview.tutorialGroups',
                },
            },
            {
                path: 'exams',
                loadChildren: () => import('./course-exams/course-exams.module').then((m) => m.CourseExamsModule),
                data: {
                    authorities: [Authority.USER],
                    pageTitle: 'overview.exams',
                },
            },
            {
                path: 'plagiarism-cases',
                loadChildren: () => import('../course/plagiarism-cases/student-view/plagiarism-cases-student-view.module').then((m) => m.ArtemisPlagiarismCasesStudentViewModule),
                data: {
                    authorities: [Authority.USER],
                    pageTitle: 'overview.plagiarismCases',
                },
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
