import { NgModule } from '@angular/core';
import { BarChartModule } from '@swimlane/ngx-charts';

import { ArtemisProgrammingExerciseActionsModule } from 'app/exercises/programming/shared/actions/programming-exercise-actions.module';
import { FeedbackCollapseComponent } from 'app/exercises/shared/feedback/collapse/feedback-collapse.component';
import { FeedbackComponent } from 'app/exercises/shared/feedback/feedback.component';
import { FeedbackNodeComponent } from 'app/exercises/shared/feedback/node/feedback-node.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisProgrammingExerciseActionsModule, ArtemisSharedComponentModule, BarChartModule],
    declarations: [FeedbackCollapseComponent, FeedbackNodeComponent, FeedbackComponent],
    exports: [FeedbackComponent],
})
export class ArtemisFeedbackModule {}
