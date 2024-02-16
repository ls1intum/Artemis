import { RouterModule, Routes } from '@angular/router';
import { NgModule } from '@angular/core';
import { Authority } from 'app/shared/constants/authority.constants';
import { RepositoryViewComponent } from 'app/localvc/repository-view/repository-view.component';
import { CommitHistoryComponent } from 'app/localvc/commit-history/commit-history.component';
import { CommitDetailsViewComponent } from 'app/localvc/commit-details-view/commit-details-view.component';
import { LocalVCGuard } from 'app/localvc/localvc-guard.service';
const routes: Routes = [
    {
        path: ':participationId',
        component: RepositoryViewComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.repository.title',
            flushRepositoryCacheAfter: 900000, // 15 min
            participationCache: {},
            repositoryCache: {},
        },
        canActivate: [LocalVCGuard],
    },
    {
        path: ':participationId/commit-history',
        component: CommitHistoryComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.repository.commitHistory.title',
            flushRepositoryCacheAfter: 900000, // 15 min
            participationCache: {},
            repositoryCache: {},
        },
        canActivate: [LocalVCGuard],
    },
    {
        path: ':participationId/commit-history/:commitHash',
        component: CommitDetailsViewComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.repository.commitHistory.commitDetails.title',
            flushRepositoryCacheAfter: 900000, // 15 min
            participationCache: {},
            repositoryCache: {},
        },
        canActivate: [LocalVCGuard],
    },
    {
        path: ':repositoryType',
        component: RepositoryViewComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.TA],
            pageTitle: 'artemisApp.repository.title',
            flushRepositoryCacheAfter: 900000, // 15 min
            participationCache: {},
            repositoryCache: {},
        },
        canActivate: [LocalVCGuard],
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule],
})
export class ArtemisProgrammingRepositoryRoutingModule {}
