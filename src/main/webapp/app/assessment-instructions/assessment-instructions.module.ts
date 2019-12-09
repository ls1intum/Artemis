import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisModelingEditorModule } from 'app/modeling-editor';
import { ArtemisSharedModule } from 'app/shared';
import { EditStructuredGradingInstructionsComponent } from './edit-structured-grading-instructions/edit-structured-grading-instructions.component';
import { ArtemisMarkdownEditorModule } from 'app/markdown-editor';
import { GradingInstructionDetailComponent } from './grading-instruction-detail/grading-instruction-detail.component';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ArtemisProgrammingExerciseInstructionsRenderModule } from 'app/entities/programming-exercise/instructions/instructions-render/programming-exercise-instructions-render.module';
import { ExpandableSectionComponent } from './expandable-section/expandable-section.component';
import { CollapsableAssessmentInstructionsComponent } from './collapsable-assessment-instructions/collapsable-assessment-instructions.component';
import { AssessmentInstructionsComponent } from './assessment-instructions/assessment-instructions.component';


@NgModule({
    declarations: [
        AssessmentInstructionsComponent,
        EditStructuredGradingInstructionsComponent,
        GradingInstructionDetailComponent,
        ExpandableSectionComponent,
        CollapsableAssessmentInstructionsComponent,
    ],
    exports: [AssessmentInstructionsComponent, EditStructuredGradingInstructionsComponent,CollapsableAssessmentInstructionsComponent],
    imports: [NgbModule, FontAwesomeModule, ArtemisSharedModule, ArtemisModelingEditorModule, ArtemisMarkdownEditorModule,CommonModule, ArtemisProgrammingExerciseInstructionsRenderModule],

})
export class AssessmentInstructionsModule {}
