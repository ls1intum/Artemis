import { NgModule } from '@angular/core';
import { RouterModule, UrlHandlingStrategy } from '@angular/router';
import { UserRouteAccessService } from './shared';
import { errorRoute, navbarRoute } from './layouts';
import { DEBUG_INFO_ENABLED } from './app.constants';
import { EditorComponent } from './editor';

const LAYOUT_ROUTES = [
    navbarRoute,
    ...errorRoute,
    /**
     * @description Routing entry for the upgraded editor component:
     * Defines the path for the editor, the participation id is provided as only argument
     */
    {
        path: 'editor/:participationId',
        component: EditorComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.editor.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

@NgModule({
    imports: [
        RouterModule.forRoot(LAYOUT_ROUTES, { useHash: true , enableTracing: DEBUG_INFO_ENABLED })
    ],
    exports: [
        RouterModule
    ]
})
export class ArTEMiSAppRoutingModule {}
