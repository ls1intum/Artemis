import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { navbarRoute } from 'app/layouts/navbar/navbar.route';
import { errorRoute } from 'app/layouts/error/error.route';

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
                    path: 'code-editor',
                    loadChildren: () => import('./code-editor/code-editor.module').then(m => m.ArtemisCodeEditorModule),
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
