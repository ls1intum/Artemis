import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';
import { UserRouteAccessService } from './shared';
import { errorRoute, navbarRoute } from './layouts';
import { EditorComponent } from './editor';

const LAYOUT_ROUTES: Routes = [
    navbarRoute,
    ...errorRoute
]; // TODO add future feature routes here, e.g. quiz, modeling, apollon, programming editor

@NgModule({
    imports: [
        RouterModule.forRoot(LAYOUT_ROUTES, { useHash: true , enableTracing: false })
    ],
    exports: [RouterModule]
})
export class ArTEMiSAppRoutingModule {}
