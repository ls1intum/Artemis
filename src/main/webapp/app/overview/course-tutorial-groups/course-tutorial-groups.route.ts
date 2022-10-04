import { Routes } from '@angular/router';
import { CourseTutorialGroupsComponent } from 'app/overview/course-tutorial-groups/course-tutorial-groups.component';
import { Authority } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { CourseTutorialGroupsOverviewComponent } from 'app/overview/course-tutorial-groups/course-tutorial-groups-overview/course-tutorial-groups-overview.component';

// parent: /courses/:courseId/tutorial-groups
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
    {
        path: 'overview',
        component: CourseTutorialGroupsOverviewComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.pages.courseTutorialGroupOverview.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
