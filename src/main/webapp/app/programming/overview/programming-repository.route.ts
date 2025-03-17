import { Routes } from '@angular/router';
import { Authority } from 'app/shared/constants/authority.constants';

import { LocalVCGuard } from 'app/programming/shared/localvc-guard.service';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

export const routes: Routes = [
    {
        path: ':repositoryId',
        loadComponent: () => import('app/programming/shared/repository-view/repository-view.component').then((m) => m.RepositoryViewComponent),
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.repository.title',
        },
        canActivate: [UserRouteAccessService, LocalVCGuard],
    },
    {
        path: ':repositoryId/commit-history',
        loadComponent: () => import('app/programming/shared/commit-history/commit-history.component').then((m) => m.CommitHistoryComponent),
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.repository.commitHistory.title',
        },
        canActivate: [UserRouteAccessService, LocalVCGuard],
    },
    {
        path: ':repositoryId/commit-history/:commitHash',
        loadComponent: () => import('app/programming/manage/commit-details-view/commit-details-view.component').then((m) => m.CommitDetailsViewComponent),
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.repository.commitHistory.commitDetails.title',
        },
        canActivate: [UserRouteAccessService, LocalVCGuard],
    },
];
