import { Routes } from '@angular/router';
import { ListOfComplaintsComponent } from 'app/assessment/manage/list-of-complaints/list-of-complaints.component';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

import { ComplaintType } from 'app/assessment/shared/entities/complaint.model';
import { IS_AT_LEAST_TUTOR } from 'app/shared/constants/authority.constants';
import { exerciseTypes } from 'app/exercise/shared/entities/exercise/exercise.model';
import { CourseManagementResolve } from 'app/core/course/manage/services/course-management-resolve.service';

export const listOfComplaintsRoute: Routes = [
    {
        path: 'complaints',
        loadComponent: () => import('app/assessment/manage/list-of-complaints/list-of-complaints.component').then((m) => m.ListOfComplaintsComponent),
        resolve: {
            course: CourseManagementResolve,
        },
        data: {
            authorities: IS_AT_LEAST_TUTOR,
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
            authorities: IS_AT_LEAST_TUTOR,
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
                authorities: IS_AT_LEAST_TUTOR,
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
            authorities: IS_AT_LEAST_TUTOR,
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
                authorities: IS_AT_LEAST_TUTOR,
                pageTitle: 'artemisApp.moreFeedback.list.title',
                complaintType: ComplaintType.MORE_FEEDBACK,
            },
            canActivate: [UserRouteAccessService],
        };
    }),
];
