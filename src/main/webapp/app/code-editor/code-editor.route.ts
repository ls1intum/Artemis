import { Routes } from '@angular/router';

import { UserRouteAccessService } from '../core';
import { PendingChangesGuard } from 'app/shared';
import { CodeEditorInstructorContainerComponent } from './mode/code-editor-instructor-container.component';
import { CodeEditorStudentContainerComponent } from './mode/code-editor-student-container.component';

export const codeEditorRoute: Routes = [
    {
        path: 'code-editor/:participationId',
        component: CodeEditorStudentContainerComponent,
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
        path: 'code-editor-admin/:exerciseId',
        component: CodeEditorInstructorContainerComponent,
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
    {
        path: 'code-editor-admin/:exerciseId/:participationId',
        component: CodeEditorInstructorContainerComponent,
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
    {
        path: 'code-editor-admin/:exerciseId/test',
        component: CodeEditorInstructorContainerComponent,
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
