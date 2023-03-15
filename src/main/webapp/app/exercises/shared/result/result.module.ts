import { NgModule } from '@angular/core';
import { BarChartModule } from '@swimlane/ngx-charts';

import { ArtemisProgrammingExerciseActionsModule } from 'app/exercises/programming/shared/actions/programming-exercise-actions.module';
import { ArtemisFeedbackModule } from 'app/exercises/shared/feedback/feedback.module';
import { ResultHistoryComponent } from 'app/overview/result-history/result-history.component';
import { SubmissionResultStatusModule } from 'app/overview/submission-result-status.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisProgrammingExerciseActionsModule, ArtemisFeedbackModule, ArtemisSharedComponentModule, SubmissionResultStatusModule, BarChartModule],
    declarations: [ResultHistoryComponent],
    exports: [ResultHistoryComponent],
})
export class ArtemisResultModule {}
