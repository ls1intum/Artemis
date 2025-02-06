import { NgModule } from '@angular/core';

import { ArtemisPlagiarismModule } from 'app/exercises/shared/plagiarism/plagiarism.module';

import { PlagiarismCaseVerdictComponent } from 'app/course/plagiarism-cases/shared/verdict/plagiarism-case-verdict.component';
import { PlagiarismCaseReviewComponent } from 'app/course/plagiarism-cases/shared/review/plagiarism-case-review.component';

@NgModule({
    imports: [ArtemisPlagiarismModule, PlagiarismCaseVerdictComponent, PlagiarismCaseReviewComponent],
    exports: [PlagiarismCaseVerdictComponent, PlagiarismCaseReviewComponent, ArtemisPlagiarismModule],
})
export class ArtemisPlagiarismCasesSharedModule {}
