import { Routes } from '@angular/router';

import { Authority } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

const routes: Routes = [
    {
        path: ':plagiarismCaseId',
        loadComponent: () =>
            import('app/course/plagiarism-cases/student-view/detail-view/plagiarism-case-student-detail-view.component').then((m) => m.PlagiarismCaseStudentDetailViewComponent),
        data: {
            authorities: [Authority.USER, Authority.ADMIN],
            pageTitle: 'artemisApp.plagiarism.cases.pageTitle',
        },
        canActivate: [UserRouteAccessService],
    },
];

export { routes };
