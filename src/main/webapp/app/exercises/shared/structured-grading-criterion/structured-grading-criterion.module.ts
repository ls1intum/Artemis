import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { EditStructuredGradingInstructionComponent } from 'app/exercises/shared/structured-grading-criterion/edit-structured-grading-instruction/edit-structured-grading-instruction.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { GradingInstructionsDetailsComponent } from 'app/exercises/shared/structured-grading-criterion/grading-instructions-details/grading-instructions-details.component.ts';

@NgModule({
    declarations: [EditStructuredGradingInstructionComponent, GradingInstructionsDetailsComponent],
    exports: [EditStructuredGradingInstructionComponent, GradingInstructionsDetailsComponent],
    imports: [CommonModule, ArtemisSharedModule, ArtemisMarkdownEditorModule],
})
export class StructuredGradingCriterionModule {}
