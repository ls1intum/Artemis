import { Routes } from '@angular/router';

import { UserRouteAccessService } from '../shared';
import { EditorComponent } from './editor.component';

export const editorRoute: Routes = [
    {
        path: 'editor',
        component: EditorComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.course.home.title'
        },
        canActivate: [UserRouteAccessService]
    }, {
        path: 'editor/:courseId/exercise/:exerciseId',
        component: EditorComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.course.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];
