import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { ListOfComplaintsComponent } from 'app/complaints/list-of-complaints/list-of-complaints.component';
import { ComplaintType } from 'app/entities/complaint.model';
import { Authority } from 'app/shared/constants/authority.constants';
import { CourseResolve } from 'app/course/manage/course-management.route';

export const listOfComplaintsRoute: Routes = [
    {
        path: ':courseId/complaints',
        component: ListOfComplaintsComponent,
        resolve: {
            course: CourseResolve,
        },
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.TA],
            pageTitle: 'artemisApp.complaint.listOfComplaints.title',
            complaintType: ComplaintType.COMPLAINT,
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/exercises/:exerciseId/complaints',
        component: ListOfComplaintsComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.TA],
            pageTitle: 'artemisApp.complaint.listOfComplaints.title',
            complaintType: ComplaintType.COMPLAINT,
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/more-feedback-requests',
        component: ListOfComplaintsComponent,
        resolve: {
            course: CourseResolve,
        },
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.TA],
            pageTitle: 'artemisApp.moreFeedback.list.title',
            complaintType: ComplaintType.MORE_FEEDBACK,
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/exercises/:exerciseId/more-feedback-requests',
        component: ListOfComplaintsComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.TA],
            pageTitle: 'artemisApp.moreFeedback.list.title',
            complaintType: ComplaintType.MORE_FEEDBACK,
        },
        canActivate: [UserRouteAccessService],
    },
];
