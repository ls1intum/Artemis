import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { MomentModule } from 'ngx-moment';
import { PlagiarismCasesComponent } from './plagiarism-cases.component';
import { ArtemisCourseScoresRoutingModule } from 'app/course/course-scores/course-scores-routing.module';
import { PlagiarismCasesListComponent } from 'app/course/plagiarism-cases/plagiarism-cases-list.component';
import { ArtemisPlagiarismModule } from 'app/exercises/shared/plagiarism/plagiarism.module';
import { ArtemisTutorParticipationGraphModule } from 'app/shared/dashboards/tutor-participation-graph/tutor-participation-graph.module';

@NgModule({
    imports: [ArtemisSharedModule, MomentModule, ArtemisCourseScoresRoutingModule, ArtemisPlagiarismModule, ArtemisTutorParticipationGraphModule],
    declarations: [PlagiarismCasesComponent, PlagiarismCasesListComponent],
})
export class PlagiarismCasesModule {}
