import { Routes } from '@angular/router';

import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { IS_AT_LEAST_EDITOR } from 'app/foundation/constants/authority.constants';

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
        // The editable code of the participation of a student, opened from the participations page. It has to precede the route below,
        // which would take "participations" for a repository type. The editor of the exercise page cannot show it, as that page always
        // shows the participation of the viewer. `fullPageEditor` makes the editor bring the instructions that page shows next to it.
        path: 'participations/:participationId',
        loadComponent: () =>
            import('app/programming/overview/code-editor-student-container/code-editor-student-container.component').then((m) => m.CodeEditorStudentContainerComponent),
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.editor.home.title',
            fullPageEditor: true,
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
