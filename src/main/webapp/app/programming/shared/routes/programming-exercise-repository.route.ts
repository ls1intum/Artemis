import { IS_AT_LEAST_INSTRUCTOR, IS_AT_LEAST_TUTOR } from 'app/foundation/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

export const repositorySubRoutes = [
    {
        path: '',
        loadComponent: () => import('app/localvc/repository-view/repository-view.component').then((m) => m.RepositoryViewComponent),
        data: {
            authorities: IS_AT_LEAST_TUTOR,
            pageTitle: 'artemisApp.repository.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'commit-history',
        loadComponent: () => import('app/localvc/commit-history/commit-history.component').then((m) => m.CommitHistoryComponent),
        data: {
            authorities: IS_AT_LEAST_TUTOR,
            pageTitle: 'artemisApp.repository.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'commit-history/:commitHash',
        loadComponent: () => import('app/programming/manage/commit-details-view/commit-details-view.component').then((m) => m.CommitDetailsViewComponent),
        data: {
            authorities: IS_AT_LEAST_TUTOR,
            pageTitle: 'artemisApp.repository.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'vcs-access-log',
        loadComponent: () =>
            import('app/programming/manage/vcs-repository-access-log-view/vcs-repository-access-log-view.component').then((m) => m.VcsRepositoryAccessLogViewComponent),
        data: {
            authorities: IS_AT_LEAST_INSTRUCTOR,
            pageTitle: 'artemisApp.repository.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
