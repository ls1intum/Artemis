import { Routes } from '@angular/router';
import { ListOfComplaintsComponent } from 'app/assessment/manage/list-of-complaints/list-of-complaints.component';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

import { ComplaintType } from 'app/entities/complaint.model';
import { Authority } from 'app/shared/constants/authority.constants';
import { exerciseTypes } from 'app/exercise/entities/exercise.model';
import { CourseManagementResolve } from 'app/course/manage/course-management-resolve.service';

export const listOfComplaintsRoute: Routes = [
    {
        path: 'complaints',
        loadComponent: () => import('app/assessment/manage/list-of-complaints/list-of-complaints.component').then((m) => m.ListOfComplaintsComponent),
        resolve: {
            course: CourseManagementResolve,
        },
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.complaint.listOfComplaints.title',
            complaintType: ComplaintType.COMPLAINT,
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'exams/:examId/complaints',
        loadComponent: () => import('app/assessment/manage/list-of-complaints/list-of-complaints.component').then((m) => m.ListOfComplaintsComponent),
        resolve: {
            course: CourseManagementResolve,
        },
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.complaint.listOfComplaints.title',
            complaintType: ComplaintType.COMPLAINT,
        },
        canActivate: [UserRouteAccessService],
    },
    ...exerciseTypes.map((exerciseType) => {
        return {
            path: exerciseType + '-exercises/:exerciseId/complaints',
            component: ListOfComplaintsComponent,
            data: {
                authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
                pageTitle: 'artemisApp.complaint.listOfComplaints.title',
                complaintType: ComplaintType.COMPLAINT,
            },
            canActivate: [UserRouteAccessService],
        };
    }),
    {
        path: 'more-feedback-requests',
        loadComponent: () => import('app/assessment/manage/list-of-complaints/list-of-complaints.component').then((m) => m.ListOfComplaintsComponent),
        resolve: {
            course: CourseManagementResolve,
        },
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.moreFeedback.list.title',
            complaintType: ComplaintType.MORE_FEEDBACK,
        },
        canActivate: [UserRouteAccessService],
    },
    ...exerciseTypes.map((exerciseType) => {
        return {
            path: exerciseType + '-exercises/:exerciseId/more-feedback-requests',
            component: ListOfComplaintsComponent,
            data: {
                authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
                pageTitle: 'artemisApp.moreFeedback.list.title',
                complaintType: ComplaintType.MORE_FEEDBACK,
            },
            canActivate: [UserRouteAccessService],
        };
    }),
];
