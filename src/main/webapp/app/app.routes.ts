import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { IS_AT_LEAST_ADMIN, IS_AT_LEAST_EDITOR, IS_AT_LEAST_USER } from 'app/shared/constants/authority.constants';
import { navbarRoute } from 'app/core/navbar/navbar.route';
import { errorRoute } from 'app/core/layouts/error/error.route';

const LAYOUT_ROUTES: Routes = [navbarRoute, ...errorRoute];

const routes: Routes = [
    ...LAYOUT_ROUTES,
    {
        path: '',
        loadComponent: () => import('./core/home/home.component').then((m) => m.HomeComponent),
        data: {
            pageTitle: 'home.title',
        },
    },
    {
        path: '',
        loadChildren: () => import('app/core/user/settings/user-settings.route').then((m) => m.routes),
        data: {
            usesModuleBackground: true,
        },
    },
    {
        path: 'admin',
        data: {
            authorities: IS_AT_LEAST_ADMIN,
            usesModuleBackground: true,
        },
        canActivate: [UserRouteAccessService],
        loadChildren: () => import('app/core/admin/admin.routes'),
    },
    {
        path: 'privacy',
        loadComponent: () => import('app/core/legal/privacy.component').then((m) => m.PrivacyComponent),
        data: {
            pageTitle: 'artemisApp.legal.privacyStatement.title',
            usesModuleBackground: true,
        },
    },
    {
        path: 'privacy/data-exports',
        loadComponent: () => import('app/core/legal/data-export/data-export.component').then((m) => m.DataExportComponent),
        data: {
            authorities: IS_AT_LEAST_USER,
            pageTitle: 'artemisApp.dataExport.title',
            usesModuleBackground: true,
        },
    },
    {
        path: 'privacy/data-exports/:id',
        loadComponent: () => import('app/core/legal/data-export/data-export.component').then((m) => m.DataExportComponent),
        data: {
            authorities: IS_AT_LEAST_USER,
            pageTitle: 'artemisApp.dataExport.title',
            usesModuleBackground: true,
        },
    },
    {
        path: 'imprint',
        loadComponent: () => import('app/core/legal/imprint.component').then((m) => m.ImprintComponent),
        data: {
            pageTitle: 'artemisApp.legal.imprint.title',
            usesModuleBackground: true,
        },
    },
    {
        path: 'about',
        loadComponent: () => import('app/core/about-us/about-us.component').then((m) => m.AboutUsComponent),
        data: {
            pageTitle: 'overview.aboutUs',
            usesModuleBackground: true,
        },
    },
    // ===== TEAM ====
    {
        path: 'course-management/:courseId/exercises/:exerciseId/teams',
        loadChildren: () => import('./exercise/team/team.route').then((m) => m.teamRoute),
        data: {
            usesModuleBackground: true,
        },
    },
    {
        path: 'courses/:courseId/exercises/:exerciseId/teams',
        loadChildren: () => import('./exercise/team/team.route').then((m) => m.teamRoute),
        data: {
            usesModuleBackground: true,
        },
    },
    // ===== ACCOUNT ====
    {
        path: 'account',
        children: [
            {
                path: 'activate',
                pathMatch: 'full',
                loadComponent: () => import('app/core/account/activate/activate.component').then((m) => m.ActivateComponent),
                data: {
                    pageTitle: 'activate.title',
                },
            },
            {
                path: 'password',
                pathMatch: 'full',
                loadComponent: () => import('app/core/account/password/password.component').then((m) => m.PasswordComponent),
                data: {
                    authorities: IS_AT_LEAST_USER,
                    pageTitle: 'global.menu.account.password',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                path: 'reset/finish',
                pathMatch: 'full',
                loadComponent: () => import('app/core/account/password-reset/finish/password-reset-finish.component').then((m) => m.PasswordResetFinishComponent),
                data: {
                    pageTitle: 'global.menu.account.password',
                },
            },
            {
                path: 'reset/request',
                pathMatch: 'full',
                loadComponent: () => import('app/core/account/password-reset/init/password-reset-init.component').then((m) => m.PasswordResetInitComponent),
                data: {
                    pageTitle: 'global.menu.account.password',
                },
            },
            {
                path: 'register',
                pathMatch: 'full',
                loadComponent: () => import('app/core/account/register/register.component').then((m) => m.RegisterComponent),
                data: {
                    pageTitle: 'register.title',
                },
            },
            {
                path: 'settings',
                pathMatch: 'full',
                loadComponent: () => import('app/core/account/settings/settings.component').then((m) => m.SettingsComponent),
                data: {
                    authorities: IS_AT_LEAST_USER,
                    pageTitle: 'global.menu.account.settings',
                },
                canActivate: [UserRouteAccessService],
            },
        ],
        data: {
            usesModuleBackground: true,
        },
    },
    // ===== COURSE MANAGEMENT =====
    {
        path: 'course-management',
        loadChildren: () => import('./core/course/manage/course-management.route').then((m) => m.courseManagementRoutes),
        data: {
            usesModuleBackground: true,
        },
    },
    {
        path: 'course-management/:courseId/programming-exercises/:exerciseId/code-editor',
        loadChildren: () => import('app/programming/manage/code-editor/code-editor-management-routes').then((m) => m.codeEditorManagementRoutes),
    },

    {
        path: 'courses',
        loadChildren: () => import('app/core/course/overview/courses.route').then((m) => m.courseRoutes),
    },
    // ===== GRADING SYSTEM =====
    {
        path: 'courses/:courseId/grading-system',
        loadChildren: () => import('./assessment/manage/grading-system/grading-system.route').then((m) => m.gradingSystemRoutes),
        data: {
            usesModuleBackground: true,
        },
    },

    {
        path: 'courses/:courseId/exercises/:exerciseId/problem-statement',
        pathMatch: 'full',
        loadComponent: () => import('app/core/course/overview/exercise-details/problem-statement/problem-statement.component').then((m) => m.ProblemStatementComponent),
    },
    {
        pathMatch: 'full',
        path: 'courses/:courseId/exercises/:exerciseId/problem-statement/:participationId',
        loadComponent: () => import('app/core/course/overview/exercise-details/problem-statement/problem-statement.component').then((m) => m.ProblemStatementComponent),
    },
    {
        path: 'courses/:courseId/exercises/:exerciseId/participations/:participationId/results/:resultId/feedback',
        pathMatch: 'full',
        data: {
            pageTitle: 'artemisApp.feedback.home.title',
        },
        loadComponent: () => import('app/exercise/feedback/standalone-feedback/standalone-feedback.component').then((m) => m.StandaloneFeedbackComponent),
    },

    // ===== EXAM =====
    {
        path: 'course-management/:courseId/exams',
        loadChildren: () => import('./exam/manage/exam-management.route').then((m) => m.examManagementRoutes),
    },
    {
        path: 'courses/:courseId/exams/:examId/grading-system',
        loadChildren: () => import('./assessment/manage/grading-system/grading-system.route').then((m) => m.gradingSystemRoutes),
    },
    {
        path: 'courses/:courseId/exams/:examId/exercises/:exerciseId/repository',
        loadChildren: () => import('./programming/overview/programming-repository.route').then((m) => m.programmingRepositoryRoutes),
    },
    {
        path: 'features',
        loadChildren: () => import('app/core/feature-overview/feature-overview.route').then((m) => m.featureOverviewRoutes),
    },
    {
        path: 'lti',
        loadChildren: () => import('./lti/shared/lti.route').then((m) => m.ltiLaunchRoutes),
    },
    {
        path: 'about-iris',
        pathMatch: 'full',
        loadComponent: () => import('app/iris/overview/about-iris/about-iris.component').then((m) => m.AboutIrisComponent),
        data: {
            pageTitle: 'artemisApp.exerciseChatbot.title',
            usesModuleBackground: true,
        },
    },
    // ===== SHARING =====
    {
        path: 'sharing/import/:basketToken',
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.sharing.title',
        },
        loadComponent: () => import('./sharing/sharing.component').then((m) => m.SharingComponent),
    },
];

export default routes;
