import { NgModule } from '@angular/core';

import { ArtemisPlagiarismModule } from 'app/exercises/shared/plagiarism/plagiarism.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { PlagiarismCaseVerdictComponent } from 'app/course/plagiarism-cases/shared/verdict/plagiarism-case-verdict.component';
import { PlagiarismCaseReviewComponent } from 'app/course/plagiarism-cases/shared/review/plagiarism-case-review.component';

@NgModule({
    imports: [ArtemisSharedComponentModule, ArtemisPlagiarismModule, PlagiarismCaseVerdictComponent, PlagiarismCaseReviewComponent],
    exports: [PlagiarismCaseVerdictComponent, PlagiarismCaseReviewComponent, ArtemisSharedComponentModule, ArtemisPlagiarismModule],
})
export class ArtemisPlagiarismCasesSharedModule {}
