import { Routes } from '@angular/router';

import { UserRouteAccessService } from '../core';
import { PendingChangesGuard } from 'app/shared';
import { CodeEditorInstructorComponent } from './code-editor-instructor.component';
import { CodeEditorStudentComponent } from './code-editor-student.component';

export const codeEditorRoute: Routes = [
    {
        path: 'code-editor/:participationId',
        component: CodeEditorStudentComponent,
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
        component: CodeEditorInstructorComponent,
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
        component: CodeEditorInstructorComponent,
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
