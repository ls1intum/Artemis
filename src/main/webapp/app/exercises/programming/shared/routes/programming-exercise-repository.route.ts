import { Authority } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { LocalVCGuard } from 'app/localvc/localvc-guard.service';

export const repositorySubRoutes = [
    {
        path: '',
        loadComponent: () => import('app/localvc/repository-view/repository-view.component').then((m) => m.RepositoryViewComponent),
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.repository.title',
        },
        canActivate: [UserRouteAccessService, LocalVCGuard],
    },
    {
        path: 'commit-history',
        loadComponent: () => import('app/localvc/commit-history/commit-history.component').then((m) => m.CommitHistoryComponent),
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.repository.title',
        },
        canActivate: [UserRouteAccessService, LocalVCGuard],
    },
    {
        path: 'commit-history/:commitHash',
        loadComponent: () => import('app/localvc/commit-details-view/commit-details-view.component').then((m) => m.CommitDetailsViewComponent),
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.repository.title',
        },
        canActivate: [UserRouteAccessService, LocalVCGuard],
    },
    {
        path: 'vcs-access-log',
        loadComponent: () => import('app/localvc/vcs-repository-access-log-view/vcs-repository-access-log-view.component').then((m) => m.VcsRepositoryAccessLogViewComponent),
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            pageTitle: 'artemisApp.repository.title',
        },
        canActivate: [UserRouteAccessService, LocalVCGuard],
    },
];
