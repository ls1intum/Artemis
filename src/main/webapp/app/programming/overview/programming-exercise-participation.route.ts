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
        // A result picked in the result history: the editor shows the submission of that result, read-only, with its feedback
        path: 'code-editor/:participationId/submission/:submissionId/result/:resultId',
        loadComponent: () => import('./code-editor-student-container/code-editor-student-container.component').then((m) => m.CodeEditorStudentContainerComponent),
        data: {
            authorities: IS_AT_LEAST_STUDENT,
            pageTitle: 'overview.exercises',
        },
        canActivate: [UserRouteAccessService],
    },
];
