import { NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { NgxGraphModule } from '@swimlane/ngx-graph';

import { CreateLearningGoalComponent } from './create-learning-goal/create-learning-goal.component';
import { EditLearningGoalComponent } from './edit-learning-goal/edit-learning-goal.component';
import { LearningGoalFormComponent } from './learning-goal-form/learning-goal-form.component';
import { LearningGoalManagementComponent } from './learning-goal-management/learning-goal-management.component';
import { LearningGoalsPopoverComponent } from './learning-goals-popover/learning-goals-popover.component';
import { LearningGoalCardComponent } from 'app/course/learning-goals/learning-goal-card/learning-goal-card.component';
import { PrerequisiteImportComponent } from 'app/course/learning-goals/learning-goal-management/prerequisite-import.component';
import { LearningGoalRingsComponent } from 'app/course/learning-goals/learning-goal-rings/learning-goal-rings.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [ArtemisSharedModule, FormsModule, ReactiveFormsModule, NgxGraphModule, ArtemisSharedComponentModule, RouterModule],
    declarations: [
        LearningGoalFormComponent,
        LearningGoalRingsComponent,
        CreateLearningGoalComponent,
        EditLearningGoalComponent,
        LearningGoalManagementComponent,
        LearningGoalCardComponent,
        LearningGoalsPopoverComponent,
        PrerequisiteImportComponent,
    ],
    exports: [LearningGoalCardComponent, LearningGoalsPopoverComponent, LearningGoalFormComponent, LearningGoalRingsComponent],
})
export class ArtemisLearningGoalsModule {}
