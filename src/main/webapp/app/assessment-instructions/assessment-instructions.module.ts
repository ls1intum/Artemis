import { NgModule } from '@angular/core';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { CommonModule } from '@angular/common';
import { CollapsableAssessmentInstructionsComponent } from 'app/assessment-instructions/collapsable-assessment-instructions/collapsable-assessment-instructions.component';
import { ExpandableSectionComponent } from 'app/assessment-instructions/expandable-section/expandable-section.component';
import { AssessmentInstructionsComponent } from 'app/assessment-instructions/assessment-instructions/assessment-instructions.component';
import { ArtemisMarkdownEditorModule } from 'app/markdown-editor/markdown-editor.module';
import { ArtemisProgrammingExerciseInstructionsRenderModule } from 'app/entities/programming-exercise/instructions/instructions-render/programming-exercise-instructions-render.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisModelingEditorModule } from 'app/modeling-editor/modeling-editor.module';

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
    declarations: [ExpandableSectionComponent, AssessmentInstructionsComponent, CollapsableAssessmentInstructionsComponent],
    exports: [AssessmentInstructionsComponent, CollapsableAssessmentInstructionsComponent],
})
export class AssessmentInstructionsModule {}
