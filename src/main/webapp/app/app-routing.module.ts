import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { navbarRoute } from 'app/shared/layouts/navbar/navbar.route';
import { errorRoute } from 'app/shared/layouts/error/error.route';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { AboutIrisComponent } from 'app/iris/about-iris/about-iris.component';

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
                {
                    path: 'privacy',
                    loadChildren: () => import('./core/legal/privacy.module').then((m) => m.ArtemisPrivacyModule),
                },
                {
                    path: 'imprint',
                    loadChildren: () => import('./core/legal/imprint.module').then((m) => m.ArtemisImprintModule),
                },
                {
                    path: 'about',
                    loadChildren: () => import('./core/about-us/artemis-about-us.module').then((module) => module.ArtemisAboutUsModule),
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

                // ===== EXAM =====
                {
                    path: 'courses/:courseId/exams/:examId',
                    loadChildren: () => import('./exam/participate/exam-participation.module').then((m) => m.ArtemisExamParticipationModule),
                },
                {
                    path: 'course-management/:courseId/exams',
                    loadChildren: () => import('./exam/manage/exam-management.module').then((m) => m.ArtemisExamManagementModule),
                },
                {
                    path: 'courses/:courseId/exams/:examId/grading-system',
                    loadChildren: () => import('./grading-system/grading-system.module').then((m) => m.GradingSystemModule),
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
                    component: AboutIrisComponent,
                    pathMatch: 'full',
                },
            ],
            { enableTracing: false, onSameUrlNavigation: 'reload' },
        ),
    ],
    exports: [RouterModule],
})
export class ArtemisAppRoutingModule {
    // Ensure the service is initialized before any routing happens
    constructor(private _: ArtemisNavigationUtilService) {}
}
