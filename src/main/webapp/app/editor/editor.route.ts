import { Routes } from '@angular/router';

import { UserRouteAccessService } from '../core';
import { EditorComponent } from './editor.component';

export const editorRoute: Routes = [
    {
        path: 'editor/:participationId',
        component: EditorComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.editor.home.title',
            flushRepositoryCacheAfter: 900000, // 15 min
            participationCache: {},
            repositoryCache: {}
        },
        canActivate: [UserRouteAccessService]
    }
];
