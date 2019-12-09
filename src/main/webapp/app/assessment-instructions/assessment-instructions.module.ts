import { NgModule } from '@angular/core';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ArtemisModelingEditorModule } from 'app/modeling-editor';
import { ArtemisSharedModule } from 'app/shared';
import { CommonModule } from '@angular/common';
import { EditStructuredGradingInstructionsComponent } from 'app/assessment-instructions/edit-structured-grading-instructions/edit-structured-grading-instructions.component';
import { CollapsableAssessmentInstructionsComponent } from 'app/assessment-instructions/collapsable-assessment-instructions/collapsable-assessment-instructions.component';
import { ExpandableSectionComponent } from 'app/assessment-instructions/expandable-section/expandable-section.component';
import { GradingInstructionDetailComponent } from 'app/assessment-instructions/grading-instruction-detail/grading-instruction-detail.component';
import { ArtemisProgrammingExerciseInstructionsRenderModule } from 'app/entities/programming-exercise/instructions/instructions-render';
import { ArtemisMarkdownEditorModule } from 'app/markdown-editor';
import { AssessmentInstructionsComponent } from 'app/assessment-instructions/assessment-instructions/assessment-instructions.component';

@NgModule({
    imports: [
        NgbModule,
        FontAwesomeModule,
        ArtemisMarkdownEditorModule,
        CommonModule,
        ArtemisProgrammingExerciseInstructionsRenderModule,
        ArtemisSharedModule,
        ArtemisModelingEditorModule,
        ArtemisProgrammingExerciseInstructionsRenderModule,
    ],
    declarations: [
        ExpandableSectionComponent,
        AssessmentInstructionsComponent,
        CollapsableAssessmentInstructionsComponent,
        EditStructuredGradingInstructionsComponent,
        GradingInstructionDetailComponent,
    ],
    exports: [AssessmentInstructionsComponent, EditStructuredGradingInstructionsComponent, CollapsableAssessmentInstructionsComponent],
})
export class AssessmentInstructionsModule {}
