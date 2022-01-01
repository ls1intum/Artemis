import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { PlagiarismCasesComponent } from './plagiarism-cases.component';
import { PlagiarismCasesListComponent } from 'app/course/plagiarism-cases/plagiarism-cases-list.component';
import { ArtemisPlagiarismModule } from 'app/exercises/shared/plagiarism/plagiarism.module';
import { ArtemisTutorParticipationGraphModule } from 'app/shared/dashboards/tutor-participation-graph/tutor-participation-graph.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { PlagiarismCasesReviewComponent } from 'app/course/plagiarism-cases/plagiarism-cases-review.component';
import { Authority } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { RouterModule, Routes } from '@angular/router';

const routes: Routes = [
    {
        path: ':plagiarismComparisonId',
        component: PlagiarismCasesReviewComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.plagiarism.cases.plagiarismReview',
        },
        canActivate: [UserRouteAccessService],
    },
];

@NgModule({
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule, ArtemisPlagiarismModule, ArtemisTutorParticipationGraphModule, RouterModule.forChild(routes)],
    declarations: [PlagiarismCasesComponent, PlagiarismCasesListComponent, PlagiarismCasesReviewComponent],
    exports: [PlagiarismCasesComponent, PlagiarismCasesReviewComponent],
})
export class PlagiarismCasesModule {}
