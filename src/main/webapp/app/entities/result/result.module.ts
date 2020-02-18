import { NgModule } from '@angular/core';

import { ArtemisSharedModule } from 'app/shared/shared.module';
import { MomentModule } from 'ngx-moment';
import { ResultHistoryComponent } from 'app/entities/result/result-history.component';
import { ArtemisProgrammingSubmissionModule } from 'app/programming-submission/programming-submission.module';
import { ArtemisProgrammingExerciseActionsModule } from 'app/entities/programming-exercise/actions/programming-exercise-actions.module';
import { SubmissionResultStatusComponent } from 'app/entities/result/submission-result-status.component';
import { UpdatingResultComponent } from 'app/entities/result/updating-result.component';
import { ResultService } from 'app/entities/result/result.service';
import { ResultComponent } from 'app/entities/result/result.component';
import { ResultDetailComponent } from 'app/entities/result/result-detail.component';

@NgModule({
    imports: [ArtemisSharedModule, MomentModule, ArtemisProgrammingSubmissionModule, ArtemisProgrammingExerciseActionsModule],
    declarations: [ResultComponent, UpdatingResultComponent, ResultDetailComponent, ResultHistoryComponent, SubmissionResultStatusComponent],
    exports: [ResultComponent, UpdatingResultComponent, ResultDetailComponent, ResultHistoryComponent, SubmissionResultStatusComponent],
    entryComponents: [ResultComponent, UpdatingResultComponent, ResultDetailComponent, SubmissionResultStatusComponent],
    providers: [ResultService],
})
export class ArtemisResultModule {}
