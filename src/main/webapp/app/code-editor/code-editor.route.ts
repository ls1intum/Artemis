import { Routes } from '@angular/router';

import { UserRouteAccessService } from '../core';
import { PendingChangesGuard } from 'app/shared';
import { CodeEditorStudentContainerComponent, CodeEditorInstructorContainerComponent } from './';

export const codeEditorRoute: Routes = [
    {
        path: ':participationId',
        component: CodeEditorStudentContainerComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'artemisApp.editor.home.title',
            flushRepositoryCacheAfter: 900000, // 15 min
            participationCache: {},
            repositoryCache: {},
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [PendingChangesGuard],
    },
    // TODO: This should probably be moved into a module of the programming exercises as it is an administratory tool. This would also be a good chance to improve the route path.
    {
        path: 'admin/:exerciseId/:participationId',
        component: CodeEditorInstructorContainerComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR'],
            pageTitle: 'artemisApp.editor.home.title',
            flushRepositoryCacheAfter: 900000, // 15 min
            participationCache: {},
            repositoryCache: {},
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [PendingChangesGuard],
    },
    {
        path: 'admin/:exerciseId/test',
        component: CodeEditorInstructorContainerComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR'],
            pageTitle: 'artemisApp.editor.home.title',
            flushRepositoryCacheAfter: 900000, // 15 min
            participationCache: {},
            repositoryCache: {},
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [PendingChangesGuard],
    },
];
