import { Routes } from '@angular/router';
import { CourseTutorialGroupsComponent } from 'app/overview/course-tutorial-groups/course-tutorial-groups.component';
import { Authority } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

export const routes: Routes = [
    {
        path: '',
        component: CourseTutorialGroupsComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'overview.tutorialGroups',
        },
        canActivate: [UserRouteAccessService],
    },
];
