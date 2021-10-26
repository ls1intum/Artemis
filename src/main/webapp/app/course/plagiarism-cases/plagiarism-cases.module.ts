import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { PlagiarismCasesComponent } from './plagiarism-cases.component';
import { PlagiarismCasesListComponent } from 'app/course/plagiarism-cases/plagiarism-cases-list.component';
import { ArtemisPlagiarismModule } from 'app/exercises/shared/plagiarism/plagiarism.module';
import { ArtemisAppRoutingModule } from 'app/app-routing.module';
import { ArtemisTutorParticipationGraphModule } from 'app/shared/dashboards/tutor-participation-graph/tutor-participation-graph.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { PlagiarismCasesReviewComponent } from 'app/course/plagiarism-cases/plagiarism-cases-review.component';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisAppRoutingModule, ArtemisSharedComponentModule, ArtemisPlagiarismModule, ArtemisTutorParticipationGraphModule],
    declarations: [PlagiarismCasesComponent, PlagiarismCasesListComponent, PlagiarismCasesReviewComponent],
    exports: [PlagiarismCasesComponent, PlagiarismCasesReviewComponent],
})
export class PlagiarismCasesModule {}
