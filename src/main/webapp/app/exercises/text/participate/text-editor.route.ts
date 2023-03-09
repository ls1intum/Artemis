import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Authority } from 'app/shared/constants/authority.constants';
import { TextEditorComponent } from './text-editor.component';

export const textEditorRoute: Routes = [
    {
        path: 'participate/:participationId',
        component: TextEditorComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.textExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
