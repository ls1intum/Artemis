import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { IS_AT_LEAST_STUDENT } from 'app/foundation/constants/authority.constants';

export const programmingExerciseParticipationRoute: Routes = [
    {
        path: 'code-editor/:participationId',
        loadComponent: () => import('./code-editor-student-container/code-editor-student-container.component').then((m) => m.CodeEditorStudentContainerComponent),
        data: {
            authorities: IS_AT_LEAST_STUDENT,
            pageTitle: 'overview.exercises',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        // An earlier submission with one of its results, opened from the result history; shown read-only at the submitted commit
        path: 'code-editor/:participationId/submission/:submissionId/result/:resultId',
        loadComponent: () => import('./code-editor-student-container/code-editor-student-container.component').then((m) => m.CodeEditorStudentContainerComponent),
        data: {
            authorities: IS_AT_LEAST_STUDENT,
            pageTitle: 'overview.exercises',
        },
        canActivate: [UserRouteAccessService],
    },
];
