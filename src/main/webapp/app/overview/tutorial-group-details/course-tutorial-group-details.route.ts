import { Routes } from '@angular/router';

import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Authority } from 'app/shared/constants/authority.constants';

const routes: Routes = [
    {
        path: '',
        loadComponent: () =>
            import('app/overview/tutorial-group-details/course-tutorial-group-detail/course-tutorial-group-detail.component').then((m) => m.CourseTutorialGroupDetailComponent),
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.pages.courseTutorialGroupDetail.title',
        },
        canActivate: [UserRouteAccessService],
    },
];

export { routes };
