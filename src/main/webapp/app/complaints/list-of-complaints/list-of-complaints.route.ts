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
            // HACK: The path is a composite, so we need to define both parts
            breadcrumbs: [
                { variable: 'course.title', path: 'course.id' },
                { label: 'artemisApp.complaint.listOfComplaints.title', path: 'complaints' },
            ],
            pageTitle: 'artemisApp.complaint.listOfComplaints.title',
            complaintType: ComplaintType.COMPLAINT,
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/exams/:examId/complaints',
        component: ListOfComplaintsComponent,
        resolve: {
            course: CourseResolve,
        },
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.TA],
            // HACK: The path is a composite, so we need to define both parts
            breadcrumbs: [
                { variable: 'course.title', path: 'course.id' },
                { label: 'artemisApp.complaint.listOfComplaints.title', path: 'complaints' },
            ],
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
            usePathForBreadcrumbs: true,
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
            // HACK: The path is a composite, so we need to define both parts
            breadcrumbs: [
                { variable: 'course.title', path: 'course.id' },
                { label: 'artemisApp.moreFeedback.list.title', path: 'more-feedback-requests' },
            ],
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
            usePathForBreadcrumbs: true,
            pageTitle: 'artemisApp.moreFeedback.list.title',
            complaintType: ComplaintType.MORE_FEEDBACK,
        },
        canActivate: [UserRouteAccessService],
    },
];
