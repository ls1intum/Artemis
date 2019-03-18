import { Routes } from '@angular/router';

import { UserRouteAccessService } from '../core';
import { CodeEditorComponent } from './code-editor.component';

export const codeEditorRoute: Routes = [
    {
        path: 'editor/:participationId',
        component: CodeEditorComponent,
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
