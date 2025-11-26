import { Routes } from '@angular/router';

import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { IS_AT_LEAST_EDITOR } from 'app/shared/constants/authority.constants';

export const codeEditorManagementRoutes: Routes = [
    {
        path: 'ide/test',
        loadComponent: () =>
            import('app/programming/manage/code-editor/instructor-and-editor-container/code-editor-instructor-and-editor-container.component').then(
                (m) => m.CodeEditorInstructorAndEditorContainerComponent,
            ),
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.editor.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'ide/:repositoryId',
        loadComponent: () =>
            import('app/programming/manage/code-editor/instructor-and-editor-container/code-editor-instructor-and-editor-container.component').then(
                (m) => m.CodeEditorInstructorAndEditorContainerComponent,
            ),
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.editor.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':repositoryType/:repositoryId',
        loadComponent: () =>
            import('app/programming/manage/code-editor/instructor-and-editor-container/code-editor-instructor-and-editor-container.component').then(
                (m) => m.CodeEditorInstructorAndEditorContainerComponent,
            ),
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.editor.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
