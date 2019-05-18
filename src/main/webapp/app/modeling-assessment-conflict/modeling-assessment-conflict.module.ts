import { NgModule } from '@angular/core';
import { ArTEMiSSharedModule } from 'app/shared';
import { SortByModule } from 'app/components/pipes';
import { AssessmentInstructionsModule } from 'app/assessment-instructions/assessment-instructions.module';
import { ModelingAssessmentModule } from 'app/modeling-assessment';
import { JhiLanguageService } from 'ng-jhipster';
import { RouterModule } from '@angular/router';
import { modelingAssessmentConflictRoutes } from 'app/modeling-assessment-conflict/modeling-assessment-conflict.route';
import { ModelingAssessmentConflictComponent } from 'app/modeling-assessment-conflict/modeling-assessment-conflict.component';
import { ConflictEscalationModalComponent } from './conflict-escalation-modal/conflict-escalation-modal.component';
import { EscalatedConflictResolutionComponent } from './escalated-conflict-resolution/escalated-conflict-resolution.component';
import { InitialConflictResolutionComponent } from './initial-conflict-resolution/initial-conflict-resolution.component';
import { ConflictControlBarComponent } from './conflict-navigation-bar/conflict-control-bar.component';

const ENTITY_STATES = [...modelingAssessmentConflictRoutes];

@NgModule({
    declarations: [
        ModelingAssessmentConflictComponent,
        ConflictEscalationModalComponent,
        EscalatedConflictResolutionComponent,
        InitialConflictResolutionComponent,
        ConflictControlBarComponent,
    ],
    imports: [ArTEMiSSharedModule, RouterModule.forChild(ENTITY_STATES), SortByModule, AssessmentInstructionsModule, ModelingAssessmentModule],
    entryComponents: [ConflictEscalationModalComponent],
    providers: [{ provide: JhiLanguageService, useClass: JhiLanguageService }],
})
export class ArTEMiSModelingAssessmentConflictModule {}
