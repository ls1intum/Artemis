import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisPlagiarismModule } from 'app/exercises/shared/plagiarism/plagiarism.module';
import { ArtemisTutorParticipationGraphModule } from 'app/shared/dashboards/tutor-participation-graph/tutor-participation-graph.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { PlagiarismCaseVerdictComponent } from 'app/course/plagiarism-cases/shared/verdict/plagiarism-case-verdict.component';
import { PlagiarismCaseReviewComponent } from 'app/course/plagiarism-cases/shared/review/plagiarism-case-review.component';

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisSharedComponentModule,
        ArtemisPlagiarismModule,
        ArtemisTutorParticipationGraphModule,
        PlagiarismCaseVerdictComponent,
        PlagiarismCaseReviewComponent,
    ],
    exports: [
        PlagiarismCaseVerdictComponent,
        PlagiarismCaseReviewComponent,
        ArtemisSharedModule,
        ArtemisSharedComponentModule,
        ArtemisPlagiarismModule,
        ArtemisTutorParticipationGraphModule,
    ],
})
export class ArtemisPlagiarismCasesSharedModule {}
