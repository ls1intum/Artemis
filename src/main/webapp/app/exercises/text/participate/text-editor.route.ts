import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { TextEditorComponent } from './text-editor.component';
import { Authority } from 'app/shared/constants/authority.constants';
import { PendingChangesGuard } from 'app/shared/guard/pending-changes.guard';

export const textEditorRoute: Routes = [
    {
        path: 'participate/:participationId',
        component: TextEditorComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.textExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [PendingChangesGuard],
    },
    {
        path: 'participate/:participationId/submission/:submissionId',
        component: TextEditorComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.textExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
