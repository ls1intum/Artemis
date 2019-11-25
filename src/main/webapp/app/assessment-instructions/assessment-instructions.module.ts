import { NgModule } from '@angular/core';
import { AssessmentInstructionsComponent } from './assessment-instructions.component';
import { ExpandableParagraphComponent } from './expandable-paragraph/expandable-paragraph.component';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { ExpandableSampleSolutionComponent } from './expandable-sample-solution/expandable-sample-solution.component';
import { ArtemisModelingEditorModule } from 'app/modeling-editor';
import { ArtemisSharedModule } from 'app/shared';
import { EditStructuredGradingInstructionsComponent } from './edit-structured-grading-instructions/edit-structured-grading-instructions.component';
import { ArtemisMarkdownEditorModule } from 'app/markdown-editor';
import { GradingInstructionDetailComponent } from './grading-instruction-detail/grading-instruction-detail.component';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
@NgModule({
    declarations: [
        AssessmentInstructionsComponent,
        ExpandableParagraphComponent,
        ExpandableSampleSolutionComponent,
        EditStructuredGradingInstructionsComponent,
        GradingInstructionDetailComponent,
    ],
    exports: [AssessmentInstructionsComponent, EditStructuredGradingInstructionsComponent],
    imports: [NgbModule, FontAwesomeModule, ArtemisSharedModule, ArtemisModelingEditorModule, ArtemisMarkdownEditorModule],

})
export class AssessmentInstructionsModule {}
