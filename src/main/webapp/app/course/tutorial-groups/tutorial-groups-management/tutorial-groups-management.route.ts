// tslint:disable:max-line-length
import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Authority } from 'app/shared/constants/authority.constants';
import { CreateTutorialGroupsConfigurationComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-groups-configuration/crud/create-tutorial-groups-configuration/create-tutorial-groups-configuration.component';
import { EditTutorialGroupsConfigurationComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-groups-configuration/crud/edit-tutorial-groups-configuration/edit-tutorial-groups-configuration.component';
import { TutorialGroupFreePeriodsManagementComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-free-periods/tutorial-free-periods-management/tutorial-group-free-periods-management.component';
import { CreateTutorialGroupFreePeriodComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-free-periods/crud/create-tutorial-group-free-period/create-tutorial-group-free-period.component';
import { EditTutorialGroupComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-groups/crud/edit-tutorial-group/edit-tutorial-group.component';
import { CreateTutorialGroupSessionComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-group-sessions/crud/create-tutorial-group-session/create-tutorial-group-session.component';
import { EditTutorialGroupSessionComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-group-sessions/crud/edit-tutorial-group-session/edit-tutorial-group-session.component';
import { TutorialGroupSessionsManagementComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-group-sessions/tutorial-group-sessions-management/tutorial-group-sessions-management.component';
import { RegisteredStudentsComponent } from 'app/course/tutorial-groups/tutorial-groups-management/registered-students/registered-students.component';
import { TutorialGroupsManagementComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-groups/tutorial-groups-management/tutorial-groups-management.component';
import { CreateTutorialGroupComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-groups/crud/create-tutorial-group/create-tutorial-group.component';
import { EditTutorialGroupFreePeriodComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-free-periods/crud/edit-tutorial-group-free-period/edit-tutorial-group-free-period.component';
import { TutorialGroupDetailComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-groups/detail/tutorial-group-detail.component';

/**
 * parent 'course-management/:courseId/tutorial-groups-management'
 */
export const tutorialGroupInstructorViewRoutes: Routes = [
    {
        path: '',
        component: TutorialGroupsManagementComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            pageTitle: 'artemisApp.pages.tutorialGroupsManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'configuration',
        component: TutorialGroupsManagementComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            pageTitle: 'artemisApp.pages.tutorialGroupsManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'configuration/create',
        component: CreateTutorialGroupsConfigurationComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            pageTitle: 'artemisApp.pages.createTutorialGroupsConfiguration.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'configuration/:tutorialGroupsConfigurationId/edit',
        component: EditTutorialGroupsConfigurationComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            pageTitle: 'artemisApp.pages.editTutorialGroupsConfiguration.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'configuration/:tutorialGroupsConfigurationId/tutorial-free-days',
        component: TutorialGroupFreePeriodsManagementComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            pageTitle: 'artemisApp.pages.tutorialFreePeriodsManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'configuration/:tutorialGroupsConfigurationId/tutorial-free-days/create',
        component: CreateTutorialGroupFreePeriodComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            pageTitle: 'artemisApp.pages.createTutorialFreePeriod.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'configuration/:tutorialGroupsConfigurationId/tutorial-free-days/:tutorialGroupFreePeriodId/edit',
        component: EditTutorialGroupFreePeriodComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            pageTitle: 'artemisApp.pages.editTutorialFreePeriod.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'create',
        component: CreateTutorialGroupComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            pageTitle: 'artemisApp.pages.createTutorialGroup.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':tutorialGroupId/edit',
        component: EditTutorialGroupComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            pageTitle: 'artemisApp.pages.editTutorialGroup.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':tutorialGroupId/sessions/create',
        component: CreateTutorialGroupSessionComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            pageTitle: 'artemisApp.pages.createTutorialSession.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':tutorialGroupId/sessions/:sessionId/edit',
        component: EditTutorialGroupSessionComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            pageTitle: 'artemisApp.pages.editTutorialSession.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':tutorialGroupId/sessions',
        component: TutorialGroupSessionsManagementComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            pageTitle: 'artemisApp.pages.tutorialGroupSessionManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':tutorialGroupId/registered-students',
        component: RegisteredStudentsComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            pageTitle: 'artemisApp.pages.registeredStudents.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':tutorialGroupId',
        component: TutorialGroupDetailComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            pageTitle: 'artemisApp.pages.tutorialGroupDetail.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
