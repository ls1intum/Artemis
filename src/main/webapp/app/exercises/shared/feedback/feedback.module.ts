import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisProgrammingExerciseActionsModule } from 'app/exercises/programming/shared/actions/programming-exercise-actions.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { FeedbackCollapseComponent } from 'app/exercises/shared/feedback/collapse/feedback-collapse.component';
import { BarChartModule } from '@swimlane/ngx-charts';
import { FeedbackNodeComponent } from 'app/exercises/shared/feedback/node/feedback-node.component';
import { FeedbackComponent } from 'app/exercises/shared/feedback/feedback.component';
import { ArtemisResultBadgeModule } from 'app/exercises/shared/result-badges/result-badges.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisProgrammingExerciseActionsModule, ArtemisSharedComponentModule, BarChartModule, ArtemisResultBadgeModule],
    declarations: [FeedbackCollapseComponent, FeedbackNodeComponent, FeedbackComponent],
    exports: [FeedbackComponent],
})
export class ArtemisFeedbackModule {}
