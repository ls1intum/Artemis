import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisProgrammingExerciseActionsModule } from 'app/exercises/programming/shared/actions/programming-exercise-actions.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { FeedbackCollapseComponent } from 'app/exercises/shared/feedback/collapse/feedback-collapse.component';
import { BarChartModule } from '@swimlane/ngx-charts';
import { FeedbackNodeComponent } from 'app/exercises/shared/feedback/node/feedback-node.component';
import { FeedbackComponent } from 'app/exercises/shared/feedback/feedback.component';
import { FeedbackTextComponent } from 'app/exercises/shared/feedback/text/feedback-text.component';
import { StandaloneFeedbackComponent } from './standalone-feedback/standalone-feedback.component';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisProgrammingExerciseActionsModule, ArtemisSharedComponentModule, BarChartModule],
    declarations: [FeedbackCollapseComponent, FeedbackNodeComponent, FeedbackComponent, FeedbackTextComponent, StandaloneFeedbackComponent],
    exports: [FeedbackComponent],
})
export class ArtemisFeedbackModule {}
