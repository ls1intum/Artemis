import { Authority } from 'app/shared/constants/authority.constants';
import { TutorialGroupsManagementComponent } from './tutorial-groups/tutorial-groups-management/tutorial-groups-management.component';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
// tslint:disable-next-line:max-line-length
import { CreateTutorialGroupComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-groups/crud/create-tutorial-group/create-tutorial-group.component';
import { RegisteredStudentsComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/registered-students/registered-students.component';
import { EditTutorialGroupComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-groups/crud/edit-tutorial-group/edit-tutorial-group.component';
import { TutorialGroupDetailComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-groups/detail/tutorial-group-detail.component';
import { Routes } from '@angular/router';

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
