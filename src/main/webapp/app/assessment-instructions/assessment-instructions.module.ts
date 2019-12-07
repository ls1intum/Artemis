import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisModelingEditorModule } from 'app/modeling-editor/modeling-editor.module';
import { ArtemisProgrammingExerciseInstructionsRenderModule } from 'app/entities/programming-exercise/instructions/instructions-render/programming-exercise-instructions-render.module';
import { ExpandableSectionComponent } from './expandable-section/expandable-section.component';
import { CollapsableAssessmentInstructionsComponent } from './collapsable-assessment-instructions/collapsable-assessment-instructions.component';
import { AssessmentInstructionsComponent } from './assessment-instructions/assessment-instructions.component';

@NgModule({
    imports: [CommonModule, NgbModule, ArtemisSharedModule, ArtemisModelingEditorModule, ArtemisProgrammingExerciseInstructionsRenderModule],
    declarations: [ExpandableSectionComponent, AssessmentInstructionsComponent, CollapsableAssessmentInstructionsComponent],
    exports: [CollapsableAssessmentInstructionsComponent],
})
export class AssessmentInstructionsModule {}
