import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { LearningGoalFormComponent } from './learning-goal-form/learning-goal-form.component';
import { CreateLearningGoalComponent } from './create-learning-goal/create-learning-goal.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { EditLearningGoalComponent } from './edit-learning-goal/edit-learning-goal.component';
import { LearningGoalManagementComponent } from './learning-goal-management/learning-goal-management.component';
import { LearningGoalCardComponent } from 'app/course/learning-goals/learning-goal-card/learning-goal-card.component';
import { LearningGoalDetailModalComponent } from './learning-goal-detail-modal/learning-goal-detail-modal.component';
import { LearningGoalsPopoverComponent } from './learning-goals-popover/learning-goals-popover.component';
import { LearningGoalCourseDetailModalComponent } from './learning-goal-course-detail-modal/learning-goal-course-detail-modal.component';
import { PrerequisiteImportComponent } from 'app/course/learning-goals/learning-goal-management/prerequisite-import.component';
import { NgxGraphModule } from '@swimlane/ngx-graph';

@NgModule({
    imports: [ArtemisSharedModule, FormsModule, ReactiveFormsModule, NgxGraphModule, ArtemisSharedComponentModule, RouterModule],
    declarations: [
        LearningGoalFormComponent,
        CreateLearningGoalComponent,
        EditLearningGoalComponent,
        LearningGoalManagementComponent,
        LearningGoalCardComponent,
        LearningGoalDetailModalComponent,
        LearningGoalsPopoverComponent,
        LearningGoalCourseDetailModalComponent,
        PrerequisiteImportComponent,
    ],
    exports: [LearningGoalCardComponent, LearningGoalsPopoverComponent, LearningGoalFormComponent],
})
export class ArtemisLearningGoalsModule {}
