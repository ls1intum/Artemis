import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';

import { ArtemisModelingEditorModule } from 'app/exercises/modeling/shared/modeling-editor.module';
import { ExpandableSectionComponent } from './expandable-section/expandable-section.component';
import { CollapsableAssessmentInstructionsComponent } from './collapsable-assessment-instructions/collapsable-assessment-instructions.component';
import { AssessmentInstructionsComponent } from './assessment-instructions/assessment-instructions.component';
import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { StructuredGradingInstructionsAssessmentLayoutComponent } from 'app/assessment/structured-grading-instructions-assessment-layout/structured-grading-instructions-assessment-layout.component';

import { OrionAssessmentInstructionsComponent } from 'app/orion/assessment/orion-assessment-instructions.component';

@NgModule({
    imports: [
        CommonModule,
        NgbModule,

        ArtemisModelingEditorModule,
        ArtemisAssessmentSharedModule,

        ExpandableSectionComponent,
        AssessmentInstructionsComponent,
        OrionAssessmentInstructionsComponent,
        CollapsableAssessmentInstructionsComponent,
        StructuredGradingInstructionsAssessmentLayoutComponent,
    ],
    exports: [
        CollapsableAssessmentInstructionsComponent,
        ExpandableSectionComponent,
        StructuredGradingInstructionsAssessmentLayoutComponent,
        AssessmentInstructionsComponent,
        OrionAssessmentInstructionsComponent,
    ],
})
export class AssessmentInstructionsModule {}
