import { Routes } from '@angular/router';
import { ApollonDiagramDetailComponent } from './apollon-diagram-detail.component';
import { ApollonDiagramListComponent } from './apollon-diagram-list.component';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { CourseResolve } from 'app/course/manage/course-management.route';
import { Authority } from 'app/shared/constants/authority.constants';

export const apollonDiagramsRoutes: Routes = [
    {
        path: ':courseId/apollon-diagrams',
        component: ApollonDiagramListComponent,
        resolve: {
            course: CourseResolve,
        },
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.TA],
            pageTitle: 'artemisApp.apollonDiagram.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/apollon-diagrams/:id',
        component: ApollonDiagramDetailComponent,
        resolve: {
            course: CourseResolve,
        },
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.TA],
            pageTitle: 'artemisApp.apollonDiagram.detail.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
