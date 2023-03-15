import { NgModule } from '@angular/core';

import { PlagiarismCaseReviewComponent } from 'app/course/plagiarism-cases/shared/review/plagiarism-case-review.component';
import { PlagiarismCaseVerdictComponent } from 'app/course/plagiarism-cases/shared/verdict/plagiarism-case-verdict.component';
import { ArtemisPlagiarismModule } from 'app/exercises/shared/plagiarism/plagiarism.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisTutorParticipationGraphModule } from 'app/shared/dashboards/tutor-participation-graph/tutor-participation-graph.module';
import { MetisModule } from 'app/shared/metis/metis.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule, ArtemisPlagiarismModule, ArtemisTutorParticipationGraphModule, MetisModule],
    declarations: [PlagiarismCaseVerdictComponent, PlagiarismCaseReviewComponent],
    exports: [
        PlagiarismCaseVerdictComponent,
        PlagiarismCaseReviewComponent,
        ArtemisSharedModule,
        ArtemisSharedComponentModule,
        ArtemisPlagiarismModule,
        ArtemisTutorParticipationGraphModule,
        MetisModule,
    ],
})
export class ArtemisPlagiarismCasesSharedModule {}
