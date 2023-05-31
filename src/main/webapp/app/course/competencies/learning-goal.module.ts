import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { LearningGoalFormComponent } from './competency-form/learning-goal-form.component';
import { CreateLearningGoalComponent } from './create-competency/create-learning-goal.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { EditLearningGoalComponent } from './edit-competency/edit-learning-goal.component';
import { CompetencyManagementComponent } from './competency-management/competency-management.component';
import { LearningGoalCardComponent } from 'app/course/competencies/competency-card/learning-goal-card.component';
import { LearningGoalsPopoverComponent } from './competencies-popover/learning-goals-popover.component';
import { PrerequisiteImportComponent } from 'app/course/competencies/competency-management/prerequisite-import.component';
import { NgxGraphModule } from '@swimlane/ngx-graph';
import { LearningGoalRingsComponent } from 'app/course/competencies/competency-rings/learning-goal-rings.component';
import { CompetencyImportComponent } from 'app/course/competencies/competency-management/competency-import.component';

@NgModule({
    imports: [ArtemisSharedModule, FormsModule, ReactiveFormsModule, NgxGraphModule, ArtemisSharedComponentModule, RouterModule],
    declarations: [
        LearningGoalFormComponent,
        LearningGoalRingsComponent,
        CreateLearningGoalComponent,
        EditLearningGoalComponent,
        CompetencyManagementComponent,
        LearningGoalCardComponent,
        LearningGoalsPopoverComponent,
        PrerequisiteImportComponent,
        CompetencyImportComponent,
    ],
    exports: [LearningGoalCardComponent, LearningGoalsPopoverComponent, LearningGoalFormComponent, LearningGoalRingsComponent],
})
export class ArtemisLearningGoalsModule {}
