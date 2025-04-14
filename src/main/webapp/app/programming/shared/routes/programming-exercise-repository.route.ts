import { Authority } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

export const repositorySubRoutes = [
    {
        path: '',
        loadComponent: () => import('app/programming/shared/repository-view/repository-view.component').then((m) => m.RepositoryViewComponent),
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.repository.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'commit-history',
        loadComponent: () => import('app/programming/shared/commit-history/commit-history.component').then((m) => m.CommitHistoryComponent),
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.repository.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'commit-history/:commitHash',
        loadComponent: () => import('app/programming/manage/commit-details-view/commit-details-view.component').then((m) => m.CommitDetailsViewComponent),
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.repository.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'vcs-access-log',
        loadComponent: () =>
            import('app/programming/manage/vcs-repository-access-log-view/vcs-repository-access-log-view.component').then((m) => m.VcsRepositoryAccessLogViewComponent),
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            pageTitle: 'artemisApp.repository.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
