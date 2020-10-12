import { RouterModule, Routes } from '@angular/router';
import { CodeEditorStudentContainerComponent } from 'app/exercises/programming/participate/code-editor-student-container.component';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { PendingChangesGuard } from 'app/shared/guard/pending-changes.guard';
import { NgModule } from '@angular/core';
import { Authority } from 'app/shared/constants/authority.constants';

const routes: Routes = [
    {
        path: ':participationId',
        component: CodeEditorStudentContainerComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.editor.home.title',
            flushRepositoryCacheAfter: 900000, // 15 min
            participationCache: {},
            repositoryCache: {},
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [PendingChangesGuard],
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule],
})
export class ArtemisProgrammingParticipationRoutingModule {}
