import { Authority } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Routes } from '@angular/router';

const plagiarismInstructorRoutes: Routes = [
    {
        path: '',
        loadComponent: () => import('app/course/plagiarism-cases/instructor-view/plagiarism-cases-instructor-view.component').then((m) => m.PlagiarismCasesInstructorViewComponent),
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.plagiarism.cases.pageTitle',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':plagiarismCaseId',
        loadComponent: () =>
            import('app/course/plagiarism-cases/instructor-view/detail-view/plagiarism-case-instructor-detail-view.component').then(
                (m) => m.PlagiarismCaseInstructorDetailViewComponent,
            ),
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.plagiarism.cases.pageTitle',
        },
        canActivate: [UserRouteAccessService],
    },
];

export { plagiarismInstructorRoutes };
