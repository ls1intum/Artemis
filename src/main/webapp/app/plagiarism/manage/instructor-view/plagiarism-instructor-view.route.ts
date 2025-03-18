import { Authority } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Routes } from '@angular/router';

export const plagiarismInstructorRoutes: Routes = [
    {
        path: '',
        loadComponent: () => import('app/plagiarism/manage/instructor-view/plagiarism-cases-instructor-view.component').then((m) => m.PlagiarismCasesInstructorViewComponent),
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.plagiarism.cases.pageTitle',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':plagiarismCaseId',
        loadComponent: () =>
            import('app/plagiarism/manage/instructor-view/detail-view/plagiarism-case-instructor-detail-view.component').then((m) => m.PlagiarismCaseInstructorDetailViewComponent),
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.plagiarism.cases.pageTitle',
        },
        canActivate: [UserRouteAccessService],
    },
];
