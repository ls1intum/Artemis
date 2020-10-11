import { NgModule } from '@angular/core';
import { LearningGoalManagementComponent } from 'app/learning-goal/learning-goal-management/learning-goal-management.component';
import { LearningGoalCardComponent } from './learning-goal-card/learning-goal-card.component';
import { ArtemisSharedLibsModule } from 'app/shared/shared-libs.module';
import { LearningGoalCreateComponent } from './learning-goal-create/learning-goal-create.component';
import { LearningGoalEditComponent } from './learning-goal-edit/learning-goal-edit.component';
import { LearningGoalFormComponent } from './learning-goal-form/learning-goal-form.component';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { LearningGoalConnectComponent } from './learning-goal-connect/learning-goal-connect.component';

@NgModule({
    declarations: [
        LearningGoalManagementComponent,
        LearningGoalCardComponent,
        LearningGoalCreateComponent,
        LearningGoalEditComponent,
        LearningGoalFormComponent,
        LearningGoalConnectComponent,
    ],
    imports: [CommonModule, ArtemisSharedLibsModule, RouterModule, ArtemisSharedCommonModule, ArtemisSharedComponentModule, ArtemisSharedModule],
})
export class ArtemisLearningGoalManagementModule {}
