import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { errorRoute, navbarRoute } from './layouts';

const LAYOUT_ROUTES: Routes = [navbarRoute, ...errorRoute];
// TODO add future feature routes here, e.g. quiz, modeling, apollon, programming editor

@NgModule({
    imports: [
        RouterModule.forRoot(
            [
                ...LAYOUT_ROUTES,
                {
                    path: 'admin',
                    loadChildren: './admin/admin.module#ArTEMiSAdminModule'
                }
            ],
            { useHash: true, enableTracing: false }
        )
    ],
    exports: [RouterModule]
})
export class ArTEMiSAppRoutingModule {}
