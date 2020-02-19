import { NgModule } from '@angular/core';

import { ArtemisSharedModule } from 'app/shared/shared.module';
import { MomentModule } from 'ngx-moment';
import { ResultHistoryComponent } from 'app/overview/result-history/result-history.component';
import { ArtemisProgrammingSubmissionModule } from 'app/exercises/programming/participate/programming-submission/programming-submission.module';
import { ArtemisProgrammingExerciseActionsModule } from 'app/exercises/programming/manage/actions/programming-exercise-actions.module';
import { SubmissionResultStatusComponent } from 'app/overview/submission-result-status.component';
import { UpdatingResultComponent } from 'app/shared/result/updating-result.component';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { ResultComponent } from 'app/shared/result/result.component';
import { ResultDetailComponent } from 'app/shared/result/result-detail.component';

@NgModule({
    imports: [ArtemisSharedModule, MomentModule, ArtemisProgrammingSubmissionModule, ArtemisProgrammingExerciseActionsModule],
    declarations: [ResultComponent, UpdatingResultComponent, ResultDetailComponent, ResultHistoryComponent, SubmissionResultStatusComponent],
    exports: [ResultComponent, UpdatingResultComponent, ResultDetailComponent, ResultHistoryComponent, SubmissionResultStatusComponent],
    entryComponents: [ResultComponent, UpdatingResultComponent, ResultDetailComponent, SubmissionResultStatusComponent],
    providers: [ResultService],
})
export class ArtemisResultModule {}
