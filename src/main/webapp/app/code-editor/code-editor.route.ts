import { Routes } from '@angular/router';

import { UserRouteAccessService } from '../core';
import { CodeEditorComponent } from './code-editor.component';
import { PendingChangesGuard } from 'app/shared';

export const codeEditorRoute: Routes = [
    {
        path: 'code-editor/:exerciseId/:participationId',
        component: CodeEditorComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.editor.home.title',
            flushRepositoryCacheAfter: 900000, // 15 min
            participationCache: {},
            repositoryCache: {},
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [PendingChangesGuard],
    },
    {
        path: 'code-editor/:exerciseId',
        component: CodeEditorComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR'],
            pageTitle: 'arTeMiSApp.editor.home.title',
            flushRepositoryCacheAfter: 900000, // 15 min
            participationCache: {},
            repositoryCache: {},
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [PendingChangesGuard],
    },
];
