import { Routes } from '@angular/router';

import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Authority } from 'app/shared/constants/authority.constants';

import { PendingChangesGuard } from 'app/shared/guard/pending-changes.guard';

import { CourseOverviewGuard } from 'app/core/course/overview/course-overview/course-overview-guard';

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
    SETTINGS = 'settings',
    TRAINING = 'training',
    TRAINING_QUIZ = 'training/quiz',
    CALENDAR = 'calendar',
}

export const routes: Routes = [
    {
        path: '',
        loadComponent: () => import('app/core/course/overview/courses/courses.component').then((m) => m.CoursesComponent),
        data: {
            authorities: [Authority.USER],
            pageTitle: 'overview.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: CourseOverviewRoutePath.ENROLL,
        loadComponent: () => import('app/core/course/overview/course-registration/course-registration.component').then((m) => m.CourseRegistrationComponent),
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.studentDashboard.enroll.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: CourseOverviewRoutePath.ARCHIVE,
        loadComponent: () => import('app/core/course/overview/course-archive/course-archive.component').then((m) => m.CourseArchiveComponent),
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
        loadComponent: () =>
            import('app/core/course/overview/course-registration/course-registration-detail/course-registration-detail.component').then((m) => m.CourseRegistrationDetailComponent),
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.studentDashboard.enroll.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId',
        loadComponent: () => import('./course-overview/course-overview.component').then((m) => m.CourseOverviewComponent),
        data: {
            authorities: [Authority.USER],
            pageTitle: 'overview.course',
        },
        canActivate: [UserRouteAccessService],
        children: [
            {
                path: CourseOverviewRoutePath.EXERCISES,
                loadComponent: () => import('app/core/course/overview/course-exercises/course-exercises.component').then((m) => m.CourseExercisesComponent),
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
                        loadComponent: () => import('app/core/course/overview/exercise-details/course-exercise-details.component').then((m) => m.CourseExerciseDetailsComponent),
                        pathMatch: 'full',
                    },
                ],
            },
            {
                path: 'exercises/text-exercises/:exerciseId',
                data: {
                    authorities: [Authority.USER],
                },
                loadChildren: () => import('app/text/overview/text-editor.route').then((m) => m.textEditorRoute),
            },
            {
                path: 'exercises/programming-exercises/:exerciseId/code-editor/:participationId',
                loadComponent: () =>
                    import('app/programming/overview/code-editor-student-container/code-editor-student-container.component').then((m) => m.CodeEditorStudentContainerComponent),
                data: {
                    authorities: [Authority.USER],
                    pageTitle: 'artemisApp.programmingExercise.home.title',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                path: 'exercises/:exerciseId/repository',
                data: {
                    authorities: [Authority.USER],
                },
                loadChildren: () => import('app/programming/overview/programming-repository.route').then((m) => m.routes),
            },
            {
                path: 'exercises/modeling-exercises/:exerciseId',
                data: {
                    authorities: [Authority.USER],
                },
                loadChildren: () => import('app/modeling/overview/modeling-participation.route').then((m) => m.routes),
            },
            {
                path: 'exercises/quiz-exercises/:exerciseId',
                data: {
                    authorities: [Authority.USER],
                },
                loadChildren: () => import('app/quiz/overview/quiz-participation.route').then((m) => m.routes),
            },
            {
                path: 'exercises/file-upload-exercises/:exerciseId/participate/:participationId',
                loadComponent: () => import('app/fileupload/overview/file-upload-submission/file-upload-submission.component').then((m) => m.FileUploadSubmissionComponent),
                data: {
                    authorities: [Authority.USER],
                    pageTitle: 'artemisApp.fileUploadExercise.home.title',
                },
                canActivate: [UserRouteAccessService],
                canDeactivate: [PendingChangesGuard],
            },

            {
                path: CourseOverviewRoutePath.LECTURES,
                loadComponent: () => import('app/lecture/shared/course-lectures/course-lectures.component').then((m) => m.CourseLecturesComponent),
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
                        loadComponent: () => import('app/lecture/overview/course-lectures/details/course-lecture-details.component').then((m) => m.CourseLectureDetailsComponent),
                    },
                ],
            },
            {
                path: CourseOverviewRoutePath.STATISTICS,
                loadChildren: () => import('app/core/course/overview/course-statistics/course-statistics.route').then((m) => m.routes),
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
                        pathMatch: 'full',
                        data: {
                            authorities: [Authority.USER],
                            pageTitle: 'overview.competencies',
                        },
                        loadComponent: () => import('app/atlas/overview/course-competencies/course-competencies.component').then((m) => m.CourseCompetenciesComponent),
                        canActivate: [UserRouteAccessService],
                    },
                    {
                        path: ':competencyId',
                        loadComponent: () =>
                            import('app/atlas/overview/course-competencies/course-competencies-details.component').then((m) => m.CourseCompetenciesDetailsComponent),
                        data: {
                            authorities: [Authority.USER],
                            pageTitle: 'overview.competencies',
                        },
                        canActivate: [UserRouteAccessService],
                    },
                ],
            },
            {
                path: CourseOverviewRoutePath.DASHBOARD,
                pathMatch: 'full',
                loadComponent: () => import('app/core/course/overview/course-dashboard/course-dashboard.component').then((m) => m.CourseDashboardComponent),
                data: {
                    authorities: [Authority.USER],
                    pageTitle: 'overview.dashboard',
                },
                canActivate: [UserRouteAccessService, CourseOverviewGuard],
            },
            {
                path: CourseOverviewRoutePath.LEARNING_PATH,
                loadComponent: () => import('app/atlas/overview/learning-path-student-page/learning-path-student-page.component').then((c) => c.LearningPathStudentPageComponent),
                data: {
                    authorities: [Authority.USER],
                    pageTitle: 'overview.learningPath',
                    showRefreshButton: true,
                },
                canActivate: [CourseOverviewGuard],
            },
            {
                path: CourseOverviewRoutePath.COMMUNICATION,
                pathMatch: 'full',
                loadComponent: () => import('app/communication/shared/course-conversations/course-conversations.component').then((m) => m.CourseConversationsComponent),
                data: {
                    authorities: [Authority.USER],
                    pageTitle: 'overview.communication',
                    hasSidebar: true,
                    showRefreshButton: true,
                },
            },
            {
                path: CourseOverviewRoutePath.SETTINGS,
                pathMatch: 'full',
                loadComponent: () => import('app/core/course/overview/course-settings/course-settings.component').then((m) => m.CourseSettingsComponent),
                data: {
                    authorities: [Authority.USER],
                    pageTitle: 'overview.settings',
                    hasSidebar: false,
                    showRefreshButton: true,
                },
            },
            {
                path: CourseOverviewRoutePath.TUTORIAL_GROUPS,
                loadComponent: () => import('app/tutorialgroup/shared/course-tutorial-groups/course-tutorial-groups.component').then((m) => m.CourseTutorialGroupsComponent),
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
                            import('app/tutorialgroup/overview/course-tutorial-group-detail-container/course-tutorial-group-detail-container.component').then(
                                (m) => m.CourseTutorialGroupDetailContainerComponent,
                            ),
                        data: {
                            authorities: [Authority.USER],
                            pageTitle: 'overview.tutorialGroups',
                            hasSidebar: true,
                            showRefreshButton: true,
                        },
                        canActivate: [UserRouteAccessService],
                    },
                    {
                        path: 'tutorial-lectures/:lectureId',
                        data: {
                            authorities: [Authority.USER],
                            pageTitle: 'overview.lectures',
                            hasSidebar: true,
                            showRefreshButton: true,
                        },
                        canActivate: [UserRouteAccessService],
                        loadComponent: () => import('app/lecture/overview/course-lectures/details/course-lecture-details.component').then((m) => m.CourseLectureDetailsComponent),
                    },
                ],
            },
            {
                path: CourseOverviewRoutePath.EXAMS,
                loadComponent: () => import('app/exam/shared/course-exams/course-exams.component').then((m) => m.CourseExamsComponent),
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
                        loadComponent: () => import('app/exam/overview/exam-participation/exam-participation.component').then((m) => m.ExamParticipationComponent),
                        data: {
                            authorities: [Authority.USER],
                            pageTitle: 'overview.exams',
                            hasSidebar: true,
                            showRefreshButton: true,
                        },
                        canActivate: [UserRouteAccessService],
                        canDeactivate: [PendingChangesGuard],
                        loadChildren: () => import('app/exam/overview/exam-participation.route').then((m) => m.examParticipationRoute),
                    },
                ],
            },
            {
                path: 'plagiarism-cases/:plagiarismCaseId',
                loadComponent: () =>
                    import('app/plagiarism/overview/detail-view/plagiarism-case-student-detail-view.component').then((m) => m.PlagiarismCaseStudentDetailViewComponent),
                data: {
                    authorities: [Authority.USER],
                    pageTitle: 'overview.plagiarismCases',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                path: CourseOverviewRoutePath.FAQ,
                loadComponent: () => import('app/communication/course-faq/course-faq.component').then((m) => m.CourseFaqComponent),
                data: {
                    authorities: [Authority.USER],
                    pageTitle: 'overview.faq',
                    hasSidebar: false,
                    showRefreshButton: true,
                },
                canActivate: [CourseOverviewGuard],
            },
            {
                path: CourseOverviewRoutePath.TRAINING,
                loadComponent: () => import('app/quiz/overview/course-training/course-training.component').then((m) => m.CourseTrainingComponent),
                data: {
                    authorities: [Authority.USER],
                    pageTitle: 'overview.training',
                    hasSidebar: false,
                    showRefreshButton: true,
                },
                canActivate: [CourseOverviewGuard],
            },
            {
                path: CourseOverviewRoutePath.TRAINING_QUIZ,
                loadComponent: () => import('app/quiz/overview/course-training/course-training-quiz/course-training-quiz.component').then((m) => m.CourseTrainingQuizComponent),
                data: {
                    authorities: [Authority.USER],
                    pageTitle: 'overview.training',
                },
                canActivate: [CourseOverviewGuard],
            },
            {
                path: '',
                redirectTo: CourseOverviewRoutePath.DASHBOARD, // dashboard will redirect to exercises if not enabled
                pathMatch: 'full',
            },
            {
                path: CourseOverviewRoutePath.CALENDAR,
                loadComponent: () => import('app/core/calendar/calendar-overview/calendar-overview.component').then((m) => m.CalendarOverviewComponent),
                data: {
                    authorities: [Authority.USER],
                    pageTitle: 'overview.calendar',
                    hasSidebar: false,
                    showRefreshButton: true,
                },
            },
        ],
    },
];
