import { NgModule } from '@angular/core';

import { ArtemisSharedModule } from 'app/shared';
import { ResultComponent, ResultDetailComponent, ResultService, UpdatingResultComponent, SubmissionResultStatusComponent } from './';
import { MomentModule } from 'ngx-moment';
import { ResultHistoryComponent } from 'app/entities/result/result-history.component';
import { ArtemisProgrammingSubmissionModule } from 'app/programming-submission/programming-submission.module';
import { ArtemisProgrammingExerciseActionsModule } from 'app/entities/programming-exercise/actions/programming-exercise-actions.module';

@NgModule({
    imports: [ArtemisSharedModule, MomentModule, ArtemisProgrammingSubmissionModule, ArtemisProgrammingExerciseActionsModule],
    declarations: [ResultComponent, UpdatingResultComponent, ResultDetailComponent, ResultHistoryComponent, SubmissionResultStatusComponent],
    exports: [ResultComponent, UpdatingResultComponent, ResultDetailComponent, ResultHistoryComponent, SubmissionResultStatusComponent],
    entryComponents: [ResultComponent, UpdatingResultComponent, ResultDetailComponent, SubmissionResultStatusComponent],
    providers: [ResultService],
})
export class ArtemisResultModule {}
