import { inject } from '@angular/core';
import { Router, Routes, UrlTree } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { IS_AT_LEAST_ADMIN, IS_AT_LEAST_EDITOR, IS_AT_LEAST_INSTRUCTOR, IS_AT_LEAST_STUDENT, IS_AT_LEAST_TUTOR } from 'app/foundation/constants/authority.constants';
import { navbarRoute } from 'app/core/navbar/navbar.route';
import { errorRoute } from 'app/core/layouts/error/error.route';
import { PasskeyAuthenticationGuard } from 'app/core/auth/passkey-authentication-guard/passkey-authentication.guard';
import { AccountService } from 'app/core/auth/account.service';

const LAYOUT_ROUTES: Routes = [navbarRoute, ...errorRoute];

const routes: Routes = [
    ...LAYOUT_ROUTES,
    {
        path: '',
        pathMatch: 'full',
        loadComponent: () => import('./core/landing/landing.component').then((m) => m.LandingComponent),
        data: {
            pageTitle: 'landing.pageTitle',
            showSkeleton: false,
        },
        canActivate: [
            (): boolean | UrlTree => {
                const accountService = inject(AccountService);
                const router = inject(Router);
                // Identity is already resolved by the APP_INITIALIZER, so check synchronously.
                // Note: when returning from a SAML2 IdP, the initializer also completes the
                // second-step JWT exchange before this guard runs, so userIdentity() is already
                // populated and no SAML-specific branch is needed here.
                if (accountService.userIdentity()) {
                    return router.parseUrl('/courses');
                }
                return true;
            },
        ],
    },
    {
        path: 'sign-in',
        loadComponent: () => import('./core/home/home.component').then((m) => m.HomeComponent),
        data: {
            pageTitle: 'home.title',
        },
    },
    {
        path: 'passkey-required',
        loadComponent: () => import('app/core/auth/passkey-authentication-page/passkey-authentication-page.component').then((m) => m.PasskeyAuthenticationPageComponent),
        data: {
            pageTitle: 'global.menu.admin.passkey-required',
            usesModuleBackground: false,
        },
    },
    {
        path: '',
        loadChildren: () => import('app/account/user/settings/user-settings.route').then((m) => m.routes),
        data: {
            usesModuleBackground: true,
        },
    },
    {
        path: 'admin',
        data: {
            authorities: IS_AT_LEAST_ADMIN,
            // The AdminContainerComponent is a self-contained layout: it renders its own module-bg sidebar and
            // module-bg content cards on the plain page background, exactly like the course layouts. It must NOT be
            // wrapped in the global `module-bg m-3 p-3` card (usesModuleBackground) — that double background makes
            // the sidebar (same module-bg) blend into the wrapper (invisible) and adds excessive left/right margin.
            usesModuleBackground: false,
        },
        canActivate: [UserRouteAccessService, PasskeyAuthenticationGuard],
        loadChildren: () => import('app/admin/admin.routes'),
    },
    // TEMPORARY (revert before merge): the ingestion dashboard normally lives at /admin/course-ingestion-dashboard,
    // behind the admin-only /admin route above. This second entry point renders the very same component for instructors
    // so the page can be exercised on the test server from an account without admin rights. Nothing about the page or
    // its data changes here - only who is allowed to reach it.
    {
        path: 'ingestion-dashboard',
        loadComponent: () => import('app/admin/course-ingestion-dashboard/course-ingestion-dashboard.component').then((m) => m.CourseIngestionDashboardComponent),
        data: {
            authorities: IS_AT_LEAST_INSTRUCTOR,
            pageTitle: 'artemisApp.courseIngestionDashboard.title',
            usesModuleBackground: true,
        },
        canActivate: [UserRouteAccessService],
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
        path: 'ai-experience-info',
        loadComponent: () => import('./logos/llm-selection-info.component').then((m) => m.LlmSelectionInfoComponent),
        data: {
            pageTitle: 'artemisApp.aiExperienceInfo.pageTitle',
        },
    },
    {
        path: 'llm-selection',
        redirectTo: 'ai-experience-info',
        pathMatch: 'full',
    },
    {
        path: 'privacy/data-exports',
        loadComponent: () => import('app/core/legal/data-export/data-export.component').then((m) => m.DataExportComponent),
        data: {
            authorities: IS_AT_LEAST_STUDENT,
            pageTitle: 'artemisApp.dataExport.title',
            usesModuleBackground: true,
        },
    },
    {
        path: 'privacy/data-exports/:id',
        loadComponent: () => import('app/core/legal/data-export/data-export.component').then((m) => m.DataExportComponent),
        data: {
            authorities: IS_AT_LEAST_STUDENT,
            pageTitle: 'artemisApp.dataExport.title',
            usesModuleBackground: true,
        },
    },
    {
        path: 'course-requests',
        loadComponent: () => import('app/course/request/course-request.component').then((m) => m.CourseRequestComponent),
        data: {
            authorities: IS_AT_LEAST_STUDENT,
            pageTitle: 'artemisApp.courseRequest.title',
            usesModuleBackground: true,
        },
        canActivate: [UserRouteAccessService],
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
    // ===== ACCOUNT ====
    {
        path: 'account',
        children: [
            {
                path: 'activate',
                pathMatch: 'full',
                loadComponent: () => import('app/account/activate/activate.component').then((m) => m.ActivateComponent),
                data: {
                    pageTitle: 'activate.title',
                },
            },
            {
                path: 'password',
                pathMatch: 'full',
                loadComponent: () => import('app/account/password/password.component').then((m) => m.PasswordComponent),
                data: {
                    authorities: IS_AT_LEAST_STUDENT,
                    pageTitle: 'global.menu.account.password',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                path: 'reset/finish',
                pathMatch: 'full',
                loadComponent: () => import('app/account/password-reset/finish/password-reset-finish.component').then((m) => m.PasswordResetFinishComponent),
                data: {
                    pageTitle: 'global.menu.account.password',
                },
            },
            {
                path: 'reset/request',
                pathMatch: 'full',
                loadComponent: () => import('app/account/password-reset/init/password-reset-init.component').then((m) => m.PasswordResetInitComponent),
                data: {
                    pageTitle: 'global.menu.account.password',
                },
            },
            {
                path: 'register',
                pathMatch: 'full',
                loadComponent: () => import('app/account/register/register.component').then((m) => m.RegisterComponent),
                data: {
                    pageTitle: 'register.title',
                },
            },
            {
                path: 'settings',
                pathMatch: 'full',
                loadComponent: () => import('app/account/settings/settings.component').then((m) => m.SettingsComponent),
                data: {
                    authorities: IS_AT_LEAST_STUDENT,
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
        // Legacy compatibility for bookmarks to the removed management overview. Keep this redirect at the root and relative as an absolute redirect inside the lazy course-management routes drops the named navbar outlet and query parameters.
        path: 'course-management',
        pathMatch: 'full',
        redirectTo: 'courses',
    },
    {
        path: 'course-management',
        loadChildren: () => import('./course/manage/course-management.route').then((m) => m.courseManagementRoutes),
        // No canActivate here, so `authorities` is read only by RoleAwarePreloadingStrategy: it lets eligible
        // staff warm this lazy parent (so its management children are discovered and preloaded) while a pure
        // student is still pruned. IS_AT_LEAST_TUTOR is the least-privileged authority any child requires;
        // per-child access control stays with each child's own guard.
        data: {
            usesModuleBackground: true,
            authorities: IS_AT_LEAST_TUTOR,
        },
    },
    {
        path: 'course-management/:courseId/programming-exercises/:exerciseId/code-editor',
        loadChildren: () => import('app/programming/manage/code-editor/code-editor-management-routes').then((m) => m.codeEditorManagementRoutes),
        // Preload-only authorities (no canActivate): least-privileged authority the code-editor routes require.
        data: {
            authorities: IS_AT_LEAST_EDITOR,
        },
    },

    {
        path: 'courses',
        loadChildren: () => import('app/course/overview/courses.route').then((m) => m.courseRoutes),
        // Preload-only authorities (no canActivate): the student course overview — warm for every authenticated
        // user so their reachable course routes are discovered and preloaded.
        data: {
            authorities: IS_AT_LEAST_STUDENT,
        },
    },
    {
        path: 'courses/:courseId/exercises/:exerciseId/problem-statement',
        pathMatch: 'full',
        loadComponent: () => import('app/course/overview/exercise-details/problem-statement/problem-statement.component').then((m) => m.ProblemStatementComponent),
    },
    {
        pathMatch: 'full',
        path: 'courses/:courseId/exercises/:exerciseId/problem-statement/:participationId',
        loadComponent: () => import('app/course/overview/exercise-details/problem-statement/problem-statement.component').then((m) => m.ProblemStatementComponent),
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
        // Preload-only authorities (no canActivate): least-privileged authority the exam-management routes require.
        data: {
            authorities: IS_AT_LEAST_TUTOR,
        },
    },
    {
        path: 'courses/:courseId/exams/:examId/grading',
        loadComponent: () => import('app/assessment/manage/grading/grading.component').then((m) => m.GradingComponent),
    },
    {
        path: 'courses/:courseId/exams/:examId/exercises/:exerciseId/repository',
        loadChildren: () => import('./programming/overview/programming-repository.route').then((m) => m.programmingRepositoryRoutes),
        // Preload-only authorities (no canActivate): least-privileged authority the repository routes require.
        data: {
            authorities: IS_AT_LEAST_STUDENT,
        },
    },
    {
        path: 'exams/rooms',
        loadComponent: () => import('app/exam/manage/students/room-distribution/exam-rooms.component').then((m) => m.ExamRoomsComponent),
        data: {
            authorities: IS_AT_LEAST_INSTRUCTOR,
            pageTitle: 'artemisApp.examRooms.management.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'features',
        loadChildren: () => import('app/core/feature-overview/feature-overview.route').then((m) => m.featureOverviewRoutes),
    },
    {
        path: 'lti',
        loadChildren: () => import('./lti/shared/lti.route').then((m) => m.ltiLaunchRoutes),
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
