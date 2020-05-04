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
                },
                {
                    path: 'account',
                    loadChildren: () => import('./account/account.module').then((m) => m.ArtemisAccountModule),
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
                    path: 'course-management/:courseId/text-exercises/:exerciseId/submissions',
                    loadChildren: () => import('./exercises/text/assess-new/text-submission-assessment.module').then((m) => m.ArtemisTextSubmissionAssessmentModule),
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
            ],
            { useHash: true, enableTracing: false },
        ),
    ],
    exports: [RouterModule],
})
export class ArtemisAppRoutingModule {}
