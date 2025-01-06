import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { PlagiarismCaseStudentDetailViewComponent } from 'app/course/plagiarism-cases/student-view/detail-view/plagiarism-case-student-detail-view.component';

import { Authority } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { ArtemisPlagiarismCasesSharedModule } from 'app/course/plagiarism-cases/shared/plagiarism-cases-shared.module';

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

@NgModule({
    imports: [RouterModule.forChild(routes), ArtemisPlagiarismCasesSharedModule, PlagiarismCaseStudentDetailViewComponent],
})
export class ArtemisPlagiarismCasesStudentViewModule {}
