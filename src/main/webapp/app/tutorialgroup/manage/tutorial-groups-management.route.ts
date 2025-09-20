/**
 * parent 'course-management/:courseId/tutorial-groups'
 */
import { Authority } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

import { Routes } from '@angular/router';

export const tutorialGroupManagementRoutes: Routes = [
    {
        path: '',
        loadComponent: () =>
            import('app/tutorialgroup/manage/tutorial-groups/tutorial-groups-management/tutorial-groups-management.component').then((m) => m.TutorialGroupsManagementComponent),
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.pages.tutorialGroupsManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'configuration',
        loadComponent: () =>
            import('app/tutorialgroup/manage/tutorial-groups/tutorial-groups-management/tutorial-groups-management.component').then((m) => m.TutorialGroupsManagementComponent),
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            pageTitle: 'artemisApp.pages.tutorialGroupsManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'configuration/:tutorialGroupsConfigurationId/edit',
        loadComponent: () =>
            import('app/tutorialgroup/manage/tutorial-groups-configuration/crud/edit-tutorial-groups-configuration/edit-tutorial-groups-configuration.component').then(
                (m) => m.EditTutorialGroupsConfigurationComponent,
            ),
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            pageTitle: 'artemisApp.pages.editTutorialGroupsConfiguration.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'configuration/:tutorialGroupsConfigurationId/tutorial-free-days',
        loadComponent: () =>
            import('app/tutorialgroup/manage/tutorial-free-periods/tutorial-free-periods-management/tutorial-group-free-periods-management.component').then(
                (m) => m.TutorialGroupFreePeriodsManagementComponent,
            ),
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            pageTitle: 'artemisApp.pages.tutorialFreePeriodsManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'create',
        loadComponent: () =>
            import('app/tutorialgroup/manage/tutorial-groups/crud/create-tutorial-group/create-tutorial-group.component').then((m) => m.CreateTutorialGroupComponent),
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            pageTitle: 'artemisApp.pages.createTutorialGroup.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':tutorialGroupId/edit',
        loadComponent: () => import('app/tutorialgroup/manage/tutorial-groups/crud/edit-tutorial-group/edit-tutorial-group.component').then((m) => m.EditTutorialGroupComponent),
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            pageTitle: 'artemisApp.pages.editTutorialGroup.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':tutorialGroupId',
        loadComponent: () =>
            import('app/tutorialgroup/manage/tutorial-groups/detail/management-tutorial-group-detail-container.component').then(
                (m) => m.ManagementTutorialGroupDetailContainerComponent,
            ),
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.pages.tutorialGroupDetail.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
