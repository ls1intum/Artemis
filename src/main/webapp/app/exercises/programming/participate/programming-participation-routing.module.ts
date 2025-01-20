import { RouterModule, Routes } from '@angular/router';

import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { NgModule } from '@angular/core';
import { Authority } from 'app/shared/constants/authority.constants';

const routes: Routes = [
    {
        path: ':participationId',
        loadComponent: () => import('app/exercises/programming/participate/code-editor-student-container.component').then((m) => m.CodeEditorStudentContainerComponent),
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.editor.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule],
})
export class ArtemisProgrammingParticipationRoutingModule {}
