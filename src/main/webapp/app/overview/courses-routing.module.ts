import { RouterModule, Routes } from '@angular/router';

import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

import { NgModule } from '@angular/core';
import { Authority } from 'app/shared/constants/authority.constants';

import { PendingChangesGuard } from 'app/shared/guard/pending-changes.guard';

import { CourseOverviewGuard } from 'app/overview/course-overview-guard';

export enum CourseOverviewRoutePath {
    DASHBOARD = 'dashboard',
    EXERCISES = 'exercises',
    EXAMS = 'exams',
    COMPETENCIES = 'competencies',
    TUTORIAL_GROUPS = 'tutorial-groups',
    FAQ = 'faq',
    LEARNING_PATH = 'learning-path',
    LECTURES = 'lectures',
    ENROLL = 'enroll',
    ARCHIVE = 'archive',
    STATISTICS = 'statistics',
    COMMUNICATION = 'communication',
}

const routes: Routes = [
    {
        path: '',
        loadComponent: () => import('app/overview/courses.component').then((m) => m.CoursesComponent),
        data: {
            authorities: [Authority.USER],
            pageTitle: 'overview.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: CourseOverviewRoutePath.ENROLL,
        loadChildren: () => import('./course-registration/course-registration.module').then((m) => m.CourseRegistrationModule),
    },
    {
        path: CourseOverviewRoutePath.ARCHIVE,
        loadComponent: () => import('./course-archive/course-archive.component').then((m) => m.CourseArchiveComponent),
        data: {
            authorities: [Authority.USER],
            pageTitle: 'overview.archive',
        },
        canActivate: [UserRouteAccessService],
    },
    // /courses/:courseId/register is special,
    // because we won't have access to the course object before the user is registered,
    // so we need to load it outside the normal course routing
    {
        path: ':courseId/register',
        loadChildren: () => import('./course-registration/course-registration-detail/course-registration-detail.module').then((m) => m.CourseRegistrationDetailModule),
    },
    {
        path: ':courseId',
        loadComponent: () => import('./course-overview.component').then((m) => m.CourseOverviewComponent),
        data: {
            authorities: [Authority.USER],
            pageTitle: 'overview.course',
        },
        canActivate: [UserRouteAccessService],
        children: [
            {
                path: CourseOverviewRoutePath.EXERCISES,
                loadComponent: () => import('app/overview/course-exercises/course-exercises.component').then((m) => m.CourseExercisesComponent),
                data: {
                    authorities: [Authority.USER],
                    pageTitle: 'overview.exercises',
                    hasSidebar: true,
                    showRefreshButton: true,
                },
                canActivate: [UserRouteAccessService],

                children: [
                    {
                        path: ':exerciseId',
                        data: {
                            authorities: [Authority.USER],
                            pageTitle: 'overview.exercises',
                            hasSidebar: true,
                            showRefreshButton: true,
                        },
                        canActivate: [UserRouteAccessService],
                        loadChildren: () => import('../overview/exercise-details/course-exercise-details.module').then((m) => m.CourseExerciseDetailsModule),
                    },
                ],
            },
            {
                path: 'exercises/text-exercises/:exerciseId',
                data: {
                    authorities: [Authority.USER],
                    pageTitle: 'overview.textExercise',
                },
                loadChildren: () => import('../exercises/text/participate/text-participation.module').then((m) => m.ArtemisTextParticipationModule),
            },
            {
                path: 'exercises/programming-exercises/:exerciseId/code-editor',
                data: {
                    authorities: [Authority.USER],
                    pageTitle: 'overview.programmingExercise',
                },
                loadChildren: () => import('../exercises/programming/participate/programming-participation.module').then((m) => m.ArtemisProgrammingParticipationModule),
            },
            {
                path: 'exercises/:exerciseId/repository',
                data: {
                    authorities: [Authority.USER],
                    pageTitle: 'overview.repository',
                },
                loadChildren: () => import('../exercises/programming/participate/programming-repository.module').then((m) => m.ArtemisProgrammingRepositoryModule),
            },
            {
                path: 'exercises/modeling-exercises/:exerciseId',
                data: {
                    authorities: [Authority.USER],
                    pageTitle: 'overview.modelingExercise',
                },
                loadChildren: () => import('../exercises/modeling/participate/modeling-participation.module').then((m) => m.ArtemisModelingParticipationModule),
            },
            {
                path: 'exercises/quiz-exercises/:exerciseId',
                data: {
                    authorities: [Authority.USER],
                    pageTitle: 'overview.quizExercise',
                },
                loadChildren: () => import('../exercises/quiz/participate/quiz-participation.module').then((m) => m.ArtemisQuizParticipationModule),
            },
            {
                path: 'exercises/file-upload-exercises/:exerciseId',
                data: {
                    authorities: [Authority.USER],
                    pageTitle: 'overview.fileUploadExercise',
                },
                loadChildren: () => import('../exercises/file-upload/participate/file-upload-participation.module').then((m) => m.ArtemisFileUploadParticipationModule),
            },

            {
                path: CourseOverviewRoutePath.LECTURES,
                loadComponent: () => import('app/overview/course-lectures/course-lectures.component').then((m) => m.CourseLecturesComponent),
                data: {
                    authorities: [Authority.USER],
                    pageTitle: 'overview.lectures',
                    hasSidebar: true,
                    showRefreshButton: true,
                },
                canActivate: [UserRouteAccessService, CourseOverviewGuard],
                children: [
                    {
                        path: ':lectureId',
                        data: {
                            authorities: [Authority.USER],
                            pageTitle: 'overview.lectures',
                            hasSidebar: true,
                            showRefreshButton: true,
                        },
                        canActivate: [UserRouteAccessService],
                        loadChildren: () => import('../overview/course-lectures/course-lecture-details.module').then((m) => m.ArtemisCourseLectureDetailsModule),
                    },
                ],
            },
            {
                path: CourseOverviewRoutePath.STATISTICS,
                loadChildren: () => import('./course-statistics/course-statistics.module').then((m) => m.CourseStatisticsModule),
                data: {
                    authorities: [Authority.USER],
                    pageTitle: 'overview.statistics',
                    showRefreshButton: true,
                },
            },
            {
                path: CourseOverviewRoutePath.COMPETENCIES,
                data: {
                    authorities: [Authority.USER],
                    pageTitle: 'overview.competencies',
                    showRefreshButton: true,
                },
                canActivate: [CourseOverviewGuard],
                children: [
                    {
                        path: '',
                        loadChildren: () => import('./course-competencies/course-competencies.module').then((m) => m.CourseCompetenciesModule),
                    },
                    {
                        path: ':competencyId',
                        loadChildren: () => import('../overview/course-competencies/course-competencies-details.module').then((m) => m.ArtemisCourseCompetenciesDetailsModule),
                    },
                ],
            },
            {
                path: CourseOverviewRoutePath.DASHBOARD,
                loadChildren: () => import('./course-dashboard/course-dashboard.module').then((m) => m.CourseDashboardModule),
                data: {
                    authorities: [Authority.USER],
                    pageTitle: 'overview.dashboard',
                },
                canActivate: [UserRouteAccessService, CourseOverviewGuard],
            },
            {
                path: CourseOverviewRoutePath.LEARNING_PATH,
                loadComponent: () =>
                    import('app/course/learning-paths/pages/learning-path-student-page/learning-path-student-page.component').then((c) => c.LearningPathStudentPageComponent),
                data: {
                    authorities: [Authority.USER],
                    pageTitle: 'overview.learningPath',
                    showRefreshButton: true,
                },
                canActivate: [CourseOverviewGuard],
            },
            {
                path: CourseOverviewRoutePath.COMMUNICATION,
                loadChildren: () => import('./course-conversations/course-conversations.module').then((m) => m.CourseConversationsModule),
                data: {
                    authorities: [Authority.USER],
                    pageTitle: 'overview.communication',
                    hasSidebar: true,
                    showRefreshButton: true,
                },
            },
            {
                path: CourseOverviewRoutePath.TUTORIAL_GROUPS,
                loadComponent: () => import('./course-tutorial-groups/course-tutorial-groups.component').then((m) => m.CourseTutorialGroupsComponent),
                data: {
                    authorities: [Authority.USER],
                    pageTitle: 'overview.tutorialGroups',
                    hasSidebar: true,
                    showRefreshButton: true,
                },
                canActivate: [UserRouteAccessService, CourseOverviewGuard],
                children: [
                    {
                        path: ':tutorialGroupId',
                        loadComponent: () =>
                            import('./tutorial-group-details/course-tutorial-group-detail/course-tutorial-group-detail.component').then(
                                (m) => m.CourseTutorialGroupDetailComponent,
                            ),
                        data: {
                            authorities: [Authority.USER],
                            pageTitle: 'overview.tutorialGroups',
                            hasSidebar: true,
                            showRefreshButton: true,
                        },
                        canActivate: [UserRouteAccessService],
                        loadChildren: () => import('../overview/tutorial-group-details/course-tutorial-group-details.module').then((m) => m.CourseTutorialGroupDetailsModule),
                    },
                ],
            },
            {
                path: CourseOverviewRoutePath.EXAMS,
                loadComponent: () => import('./course-exams/course-exams.component').then((m) => m.CourseExamsComponent),
                data: {
                    authorities: [Authority.USER],
                    pageTitle: 'overview.exams',
                    hasSidebar: true,
                    showRefreshButton: true,
                },
                canActivate: [UserRouteAccessService, CourseOverviewGuard],
                children: [
                    {
                        path: ':examId',
                        loadComponent: () => import('app/exam/participate/exam-participation.component').then((m) => m.ExamParticipationComponent),
                        data: {
                            authorities: [Authority.USER],
                            pageTitle: 'overview.exams',
                            hasSidebar: true,
                            showRefreshButton: true,
                        },
                        canActivate: [UserRouteAccessService],
                        canDeactivate: [PendingChangesGuard],
                        loadChildren: () => import('../exam/participate/exam-participation.route').then((m) => m.examParticipationRoute),
                    },
                ],
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
                path: CourseOverviewRoutePath.FAQ,
                loadComponent: () => import('../overview/course-faq/course-faq.component').then((m) => m.CourseFaqComponent),
                data: {
                    authorities: [Authority.USER],
                    pageTitle: 'overview.faq',
                    hasSidebar: false,
                    showRefreshButton: true,
                },
                canActivate: [CourseOverviewGuard],
            },
            {
                path: '',
                redirectTo: CourseOverviewRoutePath.DASHBOARD, // dashboard will redirect to exercises if not enabled
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
