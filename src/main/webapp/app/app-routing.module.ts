import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Authority } from 'app/shared/constants/authority.constants';
import { navbarRoute } from 'app/shared/layouts/navbar/navbar.route';
import { errorRoute } from 'app/shared/layouts/error/error.route';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';

const LAYOUT_ROUTES: Routes = [navbarRoute, ...errorRoute];

@NgModule({
    imports: [
        RouterModule.forRoot(
            [
                ...LAYOUT_ROUTES,
                {
                    path: '',
                    loadComponent: () => import('./home/home.component').then((m) => m.HomeComponent),
                    data: {
                        pageTitle: 'home.title',
                    },
                },
                {
                    path: 'admin',
                    loadChildren: () => import('./admin/admin.module').then((m) => m.ArtemisAdminModule),
                },
                {
                    path: 'privacy',
                    loadComponent: () => import('./core/legal/privacy.component').then((m) => m.PrivacyComponent),
                    data: {
                        pageTitle: 'artemisApp.legal.privacyStatement.title',
                    },
                },
                {
                    path: 'privacy/data-exports/:id',
                    loadComponent: () => import('./core/legal/data-export/data-export.component').then((m) => m.DataExportComponent),
                    data: {
                        authorities: [Authority.USER],
                        pageTitle: 'artemisApp.dataExport.title',
                    },
                    canActivate: [UserRouteAccessService],
                },
                {
                    path: 'privacy/data-exports',
                    loadComponent: () => import('./core/legal/data-export/data-export.component').then((m) => m.DataExportComponent),
                    data: {
                        authorities: [Authority.USER],
                        pageTitle: 'artemisApp.dataExport.title',
                    },
                    canActivate: [UserRouteAccessService],
                },
                {
                    path: 'imprint',
                    loadComponent: () => import('./core/legal/imprint.component').then((m) => m.ImprintComponent),
                    data: {
                        pageTitle: 'artemisApp.legal.imprint.title',
                    },
                },
                {
                    path: 'about',
                    loadComponent: () => import('./core/about-us/about-us.component').then((m) => m.AboutUsComponent),
                    data: {
                        pageTitle: 'overview.aboutUs',
                    },
                },
                // ===== TEAM ====
                {
                    path: 'course-management/:courseId/exercises/:exerciseId/teams',
                    loadChildren: () => import('./exercises/shared/team/team.module').then((m) => m.ArtemisTeamModule),
                },
                {
                    path: 'courses/:courseId/exercises/:exerciseId/teams',
                    loadChildren: () => import('./exercises/shared/team/team.module').then((m) => m.ArtemisTeamModule),
                },
                // ===== ACCOUNT ====
                {
                    path: 'account',
                    children: [
                        {
                            path: 'activate',
                            pathMatch: 'full',
                            loadComponent: () => import('./account/activate/activate.component').then((m) => m.ActivateComponent),
                            data: {
                                pageTitle: 'activate.title',
                            },
                        },
                        {
                            path: 'password',
                            pathMatch: 'full',
                            loadComponent: () => import('./account/password/password.component').then((m) => m.PasswordComponent),
                            data: {
                                authorities: [Authority.USER],
                                pageTitle: 'global.menu.account.password',
                            },
                            canActivate: [UserRouteAccessService],
                        },
                        {
                            path: 'reset/finish',
                            pathMatch: 'full',
                            loadComponent: () => import('./account/password-reset/finish/password-reset-finish.component').then((m) => m.PasswordResetFinishComponent),
                            data: {
                                pageTitle: 'global.menu.account.password',
                            },
                        },
                        {
                            path: 'reset/request',
                            pathMatch: 'full',
                            loadComponent: () => import('./account/password-reset/init/password-reset-init.component').then((m) => m.PasswordResetInitComponent),
                            data: {
                                pageTitle: 'global.menu.account.password',
                            },
                        },
                        {
                            path: 'register',
                            pathMatch: 'full',
                            loadComponent: () => import('./account/register/register.component').then((m) => m.RegisterComponent),
                            data: {
                                pageTitle: 'register.title',
                            },
                        },
                        {
                            path: 'settings',
                            pathMatch: 'full',
                            loadComponent: () => import('./account/settings/settings.component').then((m) => m.SettingsComponent),
                            data: {
                                authorities: [Authority.USER],
                                pageTitle: 'global.menu.account.settings',
                            },
                            canActivate: [UserRouteAccessService],
                        },
                    ],
                },
                // ===== COURSE MANAGEMENT =====
                {
                    path: 'course-management',
                    loadChildren: () => import('./course/manage/course-management.module').then((m) => m.ArtemisCourseManagementModule),
                },
                {
                    path: 'course-management/:courseId/programming-exercises/:exerciseId/code-editor',
                    loadChildren: () => import('./exercises/programming/manage/code-editor/code-editor-management.module').then((m) => m.ArtemisCodeEditorManagementModule),
                },
                {
                    path: 'course-management/:courseId/text-exercises/:exerciseId',
                    loadChildren: () => import('./exercises/text/assess/text-submission-assessment.module').then((m) => m.ArtemisTextSubmissionAssessmentModule),
                },
                {
                    path: 'course-management/:courseId/text-exercises/:exerciseId/example-submissions/:exampleSubmissionId',
                    loadChildren: () => import('./exercises/text/manage/example-text-submission/example-text-submission.module').then((m) => m.ArtemisExampleTextSubmissionModule),
                },
                {
                    path: 'course-management/:courseId/programming-exercises/:exerciseId',
                    loadChildren: () =>
                        import('./exercises/programming/manage/programming-exercise-management-routing.module').then((m) => m.ArtemisProgrammingExerciseManagementRoutingModule),
                },
                {
                    path: 'courses',
                    loadChildren: () => import('./overview/courses.module').then((m) => m.ArtemisCoursesModule),
                },
                // ===== GRADING SYSTEM =====
                {
                    path: 'courses/:courseId/grading-system',
                    loadChildren: () => import('./grading-system/grading-system.module').then((m) => m.GradingSystemModule),
                },

                {
                    path: 'courses/:courseId/exercises/:exerciseId/problem-statement',
                    pathMatch: 'full',
                    loadComponent: () => import('./overview/exercise-details/problem-statement/problem-statement.component').then((m) => m.ProblemStatementComponent),
                },
                {
                    pathMatch: 'full',
                    path: 'courses/:courseId/exercises/:exerciseId/problem-statement/:participationId',
                    loadComponent: () => import('./overview/exercise-details/problem-statement/problem-statement.component').then((m) => m.ProblemStatementComponent),
                },
                {
                    path: 'courses/:courseId/exercises/:exerciseId/participations/:participationId/results/:resultId/feedback',
                    pathMatch: 'full',
                    loadComponent: () => import('./exercises/shared/feedback/standalone-feedback/standalone-feedback.component').then((m) => m.StandaloneFeedbackComponent),
                },

                // ===== EXAM =====
                {
                    path: 'course-management/:courseId/exams',
                    loadChildren: () => import('./exam/manage/exam-management.module').then((m) => m.ArtemisExamManagementModule),
                },
                {
                    path: 'courses/:courseId/exams/:examId/grading-system',
                    loadChildren: () => import('./grading-system/grading-system.module').then((m) => m.GradingSystemModule),
                },
                {
                    path: 'courses/:courseId/exams/:examId/exercises/:exerciseId/repository',
                    loadChildren: () =>
                        import('./exercises/programming/participate/programming-repository-routing.module').then((m) => m.ArtemisProgrammingRepositoryRoutingModule),
                },
                {
                    path: 'features',
                    loadChildren: () => import('./feature-overview/feature-overview.module').then((m) => m.FeatureOverviewModule),
                },
                {
                    path: 'lti',
                    loadChildren: () => import('./lti/lti.module').then((m) => m.ArtemisLtiModule),
                },
                {
                    path: 'about-iris',
                    pathMatch: 'full',
                    loadComponent: () => import('./iris/about-iris/about-iris.component').then((m) => m.AboutIrisComponent),
                },
            ],
            { enableTracing: false, onSameUrlNavigation: 'reload' },
        ),
    ],
    exports: [RouterModule],
})
export class ArtemisAppRoutingModule {
    // Ensure the service is initialized before any routing happens
    constructor(private _service: ArtemisNavigationUtilService) {}
}
