import { Routes } from '@angular/router';
import { Authority } from 'app/shared/constants/authority.constants';

import { LocalVCGuard } from 'app/localvc/localvc-guard.service';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

const routes: Routes = [
    {
        path: ':repositoryId',
        loadComponent: () => import('app/localvc/repository-view/repository-view.component').then((m) => m.RepositoryViewComponent),
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.repository.title',
        },
        canActivate: [UserRouteAccessService, LocalVCGuard],
    },
    {
        path: ':repositoryId/commit-history',
        loadComponent: () => import('app/localvc/commit-history/commit-history.component').then((m) => m.CommitHistoryComponent),
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.repository.commitHistory.title',
        },
        canActivate: [UserRouteAccessService, LocalVCGuard],
    },
    {
        path: ':repositoryId/commit-history/:commitHash',
        loadComponent: () => import('app/localvc/commit-details-view/commit-details-view.component').then((m) => m.CommitDetailsViewComponent),
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.repository.commitHistory.commitDetails.title',
        },
        canActivate: [UserRouteAccessService, LocalVCGuard],
    },
];
export { routes };
