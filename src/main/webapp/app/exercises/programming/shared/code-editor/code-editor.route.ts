import { Routes } from '@angular/router';

import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { PendingChangesGuard } from 'app/shared/guard/pending-changes.guard';
import { CodeEditorInstructorContainerComponent } from 'app/exercises/programming/shared/code-editor/instructor/code-editor-instructor-container.component';
import { CodeEditorStudentContainerComponent } from 'app/exercises/programming/shared/code-editor/code-editor-student-container.component';
import { CodeEditorInstructorOrionContainerComponent } from 'app/exercises/programming/shared/code-editor/instructor/code-editor-instructor-orion-container.component';

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
        path: ':exerciseId/admin/:participationId',
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
        path: ':exerciseId/admin/test',
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
        path: 'ide/:exerciseId/admin/test',
        component: CodeEditorInstructorOrionContainerComponent,
        data: {
            authorities: ['ROLE_INSTRUCTOR'],
            pageTitle: 'artemisApp.editor.home.title',
            flushRepositoryCacheAfter: 900000, // 15 min
            participationCache: {},
            repositoryCache: {},
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [PendingChangesGuard],
    },
    {
        path: 'ide/:exerciseId/admin/:participationId',
        component: CodeEditorInstructorOrionContainerComponent,
        data: {
            authorities: ['ROLE_INSTRUCTOR'],
            pageTitle: 'artemisApp.editor.home.title',
            flushRepositoryCacheAfter: 900000, // 15 min
            participationCache: {},
            repositoryCache: {},
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [PendingChangesGuard],
    },
];
