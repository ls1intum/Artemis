import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { navbarRoute } from 'app/shared/layouts/navbar/navbar.route';
import { errorRoute } from 'app/shared/layouts/error/error.route';

const LAYOUT_ROUTES: Routes = [navbarRoute, ...errorRoute];

@NgModule({
    imports: [
        RouterModule.forRoot(
            [
                ...LAYOUT_ROUTES,
                {
                    path: 'admin',
                    loadChildren: () => import('./admin/admin.module').then((m) => m.ArtemisAdminModule),
                    data: {
                        pageTitle: 'global.menu.admin.main',
                    },
                },
                {
                    path: 'account',
                    loadChildren: () => import('./account/account.module').then((m) => m.ArtemisAccountModule),
                    data: {
                        pageTitle: 'global.menu.account.main',
                    },
                },
                {
                    path: 'about',
                    loadChildren: () => import('./core/about-us/artemis-about-us.module').then((module) => module.ArtemisAboutUsModule),
                    data: {
                        pageTitle: 'aboutUs',
                    },
                },
                // ===== COURSE MANAGEMENT =====
                {
                    path: 'course-management',
                    loadChildren: () => import('./course/manage/course-management.module').then((m) => m.ArtemisCourseManagementModule),
                    data: {
                        pageTitle: 'global.menu.course',
                    },
                },
                {
                    path: 'course-management/:courseId/programming-exercises/:exerciseId/code-editor',
                    loadChildren: () => import('./exercises/programming/manage/code-editor/code-editor-management.module').then((m) => m.ArtemisCodeEditorManagementModule),
                    data: {
                        usePathForBreadcrumbs: true,
                    },
                },
                {
                    path: 'course-management/:courseId/text-exercises/:exerciseId',
                    loadChildren: () => import('./exercises/text/assess/text-submission-assessment.module').then((m) => m.ArtemisTextSubmissionAssessmentModule),
                },
                {
                    path: 'course-management/:courseId/text-exercises/:exerciseId/example-submissions/:exampleSubmissionId',
                    loadChildren: () => import('./exercises/text/manage/example-text-submission/example-text-submission.module').then((m) => m.ArtemisExampleTextSubmissionModule),
                },
                // ===== COURSES =====
                {
                    path: 'courses/:courseId/programming-exercises/:exerciseId/code-editor',
                    loadChildren: () => import('./exercises/programming/participate/programming-participation.module').then((m) => m.ArtemisProgrammingParticipationModule),
                },
                {
                    path: 'courses/:courseId/modeling-exercises/:exerciseId',
                    loadChildren: () => import('./exercises/modeling/participate/modeling-participation.module').then((m) => m.ArtemisModelingParticipationModule),
                },
                {
                    path: 'courses/:courseId/quiz-exercises/:exerciseId',
                    loadChildren: () => import('./exercises/quiz/participate/quiz-participation.module').then((m) => m.ArtemisQuizParticipationModule),
                },
                {
                    path: 'courses/:courseId/text-exercises/:exerciseId',
                    loadChildren: () => import('./exercises/text/participate/text-participation.module').then((m) => m.ArtemisTextParticipationModule),
                },
                {
                    path: 'courses/:courseId/file-upload-exercises/:exerciseId',
                    loadChildren: () => import('./exercises/file-upload/participate/file-upload-participation.module').then((m) => m.ArtemisFileUploadParticipationModule),
                },
                // ===== EXAM =====
                {
                    path: 'courses/:courseId/exams/:examId',
                    loadChildren: () => import('./exam/participate/exam-participation.module').then((m) => m.ArtemisExamParticipationModule),
                },
                {
                    path: 'course-management/:courseId/exams',
                    loadChildren: () => import('./exam/manage/exam-management.module').then((m) => m.ArtemisExamManagementModule),
                    data: {
                        usePathForBreadcrumbs: true,
                    },
                },
                {
                    path: 'features',
                    loadChildren: () => import('./feature-overview/feature-overview.module').then((m) => m.FeatureOverviewModule),
                },
            ],
            { enableTracing: false, onSameUrlNavigation: 'reload' },
        ),
    ],
    exports: [RouterModule],
})
export class ArtemisAppRoutingModule {}
