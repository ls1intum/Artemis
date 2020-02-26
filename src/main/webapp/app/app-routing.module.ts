import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { navbarRoute } from 'app/shared/layouts/navbar/navbar.route';
import { errorRoute } from 'app/shared/layouts/error/error.route';

const LAYOUT_ROUTES: Routes = [navbarRoute, ...errorRoute];
// TODO add future feature routes here, e.g. quiz, modeling, apollon, programming editor

@NgModule({
    imports: [
        RouterModule.forRoot(
            [
                ...LAYOUT_ROUTES,
                {
                    path: 'admin',
                    loadChildren: () => import('./admin/admin.module').then(m => m.ArtemisAdminModule),
                },
                {
                    path: 'courses/:courseId/programming-exercises/:exerciseId/code-editor',
                    loadChildren: () => import('./exercises/programming/participate/programming-participation.module').then(m => m.ArtemisProgrammingParticipationModule),
                },
                {
                    path: 'course-management/:courseId/programming-exercises/:exerciseId/code-editor',
                    loadChildren: () => import('./exercises/programming/manage/code-editor/code-editor-management.module').then(m => m.ArtemisCodeEditorManagementModule),
                },
                {
                    path: 'account',
                    loadChildren: () => import('./account/account.module').then(m => m.ArtemisAccountModule),
                },
            ],
            { useHash: true, enableTracing: false },
        ),
    ],
    exports: [RouterModule],
})
export class ArtemisAppRoutingModule {}
