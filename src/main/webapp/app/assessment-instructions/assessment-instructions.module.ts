import { NgModule } from '@angular/core';
import { ExpandableParagraphComponent } from './expandable-paragraph/expandable-paragraph.component';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { ExpandableSampleSolutionComponent } from './expandable-sample-solution/expandable-sample-solution.component';
import { ArtemisModelingEditorModule } from 'app/modeling-editor';
import { ArtemisSharedModule } from 'app/shared';
import { ExpandableProblemStatementComponent } from './expandable-problem-statement/expandable-problem-statement.component';
import { ArtemisProgrammingExerciseInstructionsRenderModule } from 'app/entities/programming-exercise/instructions/instructions-render';
import { AssessmentInstructionsComponent } from 'app/assessment-instructions/assessment-instructions.component';

@NgModule({
    declarations: [AssessmentInstructionsComponent, ExpandableParagraphComponent, ExpandableSampleSolutionComponent, ExpandableProblemStatementComponent],
    exports: [AssessmentInstructionsComponent, ExpandableSampleSolutionComponent],
    imports: [NgbModule, ArtemisSharedModule, ArtemisModelingEditorModule, ArtemisProgrammingExerciseInstructionsRenderModule],
})
export class AssessmentInstructionsModule {}
