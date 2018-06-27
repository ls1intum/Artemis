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

export class CustomHandlingStrategy implements UrlHandlingStrategy {
    /**
     * @class CustomHandlingStrategy
     * @implements UrlHandlingStrategy
     * @desc Can be used to tell Angular whether to handle a given URL or not.
     * Useful when in need to handle a dual-router setup. Currently, the Angular router handles
     * every URL change, so this just return 'true'
     */
    shouldProcessUrl(url) {
        console.log('shouldProcessUrl: ' + url);
        return true;
    }
    extract(url) { return url; }
    merge(url, whole) { return url; }
}

@NgModule({
    imports: [
        RouterModule.forRoot(LAYOUT_ROUTES, { useHash: true , enableTracing: DEBUG_INFO_ENABLED })
    ],
    exports: [
        RouterModule
    ],
    providers: [
        { provide: UrlHandlingStrategy, useClass: CustomHandlingStrategy }
    ],
})
export class ArTEMiSAppRoutingModule {}
