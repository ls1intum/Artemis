import { Routes } from '@angular/router';

import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Authority } from 'app/shared/constants/authority.constants';

export const routes: Routes = [
    {
        path: 'test',
        loadComponent: () =>
            import('app/exercises/programming/manage/code-editor/code-editor-instructor-and-editor-container.component').then(
                (m) => m.CodeEditorInstructorAndEditorContainerComponent,
            ),
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.editor.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'ide/test',
        loadComponent: () =>
            import('app/orion/management/code-editor-instructor-and-editor-orion-container.component').then((m) => m.CodeEditorInstructorAndEditorOrionContainerComponent),
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.editor.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'ide/:participationId',
        loadComponent: () =>
            import('app/orion/management/code-editor-instructor-and-editor-orion-container.component').then((m) => m.CodeEditorInstructorAndEditorOrionContainerComponent),
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.editor.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':participationId',
        loadComponent: () =>
            import('app/exercises/programming/manage/code-editor/code-editor-instructor-and-editor-container.component').then(
                (m) => m.CodeEditorInstructorAndEditorContainerComponent,
            ),
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.editor.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'auxiliary/:repositoryId',
        loadComponent: () =>
            import('app/exercises/programming/manage/code-editor/code-editor-instructor-and-editor-container.component').then(
                (m) => m.CodeEditorInstructorAndEditorContainerComponent,
            ),
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.editor.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
