import { NgModule } from '@angular/core';
import { LearningGoalManagementComponent } from 'app/learning-goal/learning-goal-management/learning-goal-management.component';
import { LearningGoalCardComponent } from './learning-goal-card/learning-goal-card.component';
import { ArtemisSharedLibsModule } from 'app/shared/shared-libs.module';

@NgModule({
    declarations: [LearningGoalManagementComponent, LearningGoalCardComponent],
    imports: [ArtemisSharedLibsModule],
})
export class ArtemisLearningGoalManagementModule {}
