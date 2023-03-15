import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';

import { AssessmentInstructionsComponent } from './assessment-instructions/assessment-instructions.component';
import { CollapsableAssessmentInstructionsComponent } from './collapsable-assessment-instructions/collapsable-assessment-instructions.component';
import { ExpandableSectionComponent } from './expandable-section/expandable-section.component';
import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { StructuredGradingInstructionsAssessmentLayoutComponent } from 'app/assessment/structured-grading-instructions-assessment-layout/structured-grading-instructions-assessment-layout.component';
import { ArtemisModelingEditorModule } from 'app/exercises/modeling/shared/modeling-editor.module';
import { ArtemisProgrammingExerciseInstructionsRenderModule } from 'app/exercises/programming/shared/instructions-render/programming-exercise-instructions-render.module';
import { OrionAssessmentInstructionsComponent } from 'app/orion/assessment/orion-assessment-instructions.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [
        CommonModule,
        NgbModule,
        ArtemisSharedModule,
        ArtemisModelingEditorModule,
        ArtemisProgrammingExerciseInstructionsRenderModule,
        ArtemisAssessmentSharedModule,
        ArtemisSharedComponentModule,
        ArtemisMarkdownModule,
    ],
    declarations: [
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
