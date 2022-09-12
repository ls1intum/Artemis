import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Authority } from 'app/shared/constants/authority.constants';
import { CreateTutorialGroupsConfigurationComponent } from 'app/course/tutorial-groups/tutorial-groups-configuration/crud/create-tutorial-groups-configuration/create-tutorial-groups-configuration/create-tutorial-groups-configuration.component';
import { EditTutorialGroupsConfigurationComponent } from 'app/course/tutorial-groups/tutorial-groups-configuration/crud/edit-tutorial-groups-configuration/edit-tutorial-groups-configuration.component';
import { TutorialGroupFreePeriodsComponent } from 'app/course/tutorial-groups/tutorial-free-periods/tutorial-group-free-periods.component';
import { CreateTutorialGroupFreePeriodComponent } from 'app/course/tutorial-groups/tutorial-free-periods/crud/create-tutorial-group-free-period/create-tutorial-group-free-period.component';
import { CreateTutorialGroupComponent } from 'app/course/tutorial-groups/crud/create-tutorial-group/create-tutorial-group.component';
import { EditTutorialGroupComponent } from 'app/course/tutorial-groups/crud/edit-tutorial-group/edit-tutorial-group.component';
import { CreateTutorialGroupSessionComponent } from 'app/course/tutorial-groups/tutorial-groups-session/crud/create-tutorial-group-session/create-tutorial-group-session.component';
import { EditTutorialGroupSessionComponent } from 'app/course/tutorial-groups/tutorial-groups-session/crud/edit-tutorial-group-session/edit-tutorial-group-session.component';
import { SessionManagementComponent } from 'app/course/tutorial-groups/session-management/session-management.component';
import { RegisteredStudentsComponent } from 'app/course/tutorial-groups/registered-students/registered-students.component';
import { TutorialGroupsManagementComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-groups-management.component';

/**
 * parent 'course-management/:courseId/tutorial-groups-management'
 */
export const tutorialGroupRoutes: Routes = [
    {
        path: '',
        component: TutorialGroupsManagementComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            pageTitle: 'artemisApp.createTutorialGroup.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'configuration/create',
        component: CreateTutorialGroupsConfigurationComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            pageTitle: 'artemisApp.createTutorialGroup.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'configuration/:tutorialGroupsConfigurationId/edit',
        component: EditTutorialGroupsConfigurationComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            pageTitle: 'artemisApp.createTutorialGroup.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'configuration/:tutorialGroupsConfigurationId/tutorial-free-days',
        component: TutorialGroupFreePeriodsComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            pageTitle: 'artemisApp.createTutorialGroup.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'configuration/:tutorialGroupsConfigurationId/tutorial-free-days/create',
        component: CreateTutorialGroupFreePeriodComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            pageTitle: 'artemisApp.createTutorialGroup.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'create',
        component: CreateTutorialGroupComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            pageTitle: 'artemisApp.createTutorialGroup.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':tutorialGroupId/edit',
        component: EditTutorialGroupComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            pageTitle: 'artemisApp.editTutorialGroup.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':tutorialGroupId/sessions/create',
        component: CreateTutorialGroupSessionComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            pageTitle: 'artemisApp.editTutorialGroup.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':tutorialGroupId/sessions/:sessionId/edit',
        component: EditTutorialGroupSessionComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            pageTitle: 'artemisApp.editSession.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':tutorialGroupId/schedule-management',
        component: SessionManagementComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            pageTitle: 'artemisApp.editTutorialGroup.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':tutorialGroupId/registered-students',
        component: RegisteredStudentsComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            pageTitle: 'artemisApp.registeredStudents.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
