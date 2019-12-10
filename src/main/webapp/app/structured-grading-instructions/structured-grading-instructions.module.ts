import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ArtemisMarkdownEditorModule } from 'app/markdown-editor';
import { EditStructuredGradingInstructionsComponent } from 'app/structured-grading-instructions/edit-structured-grading-instructions/edit-structured-grading-instructions.component';
import { GradingInstructionDetailComponent } from 'app/structured-grading-instructions/grading-instruction-detail/grading-instruction-detail.component';
import { ArtemisSharedModule } from 'app/shared';

@NgModule({
    declarations: [GradingInstructionDetailComponent, EditStructuredGradingInstructionsComponent],
    exports: [GradingInstructionDetailComponent, EditStructuredGradingInstructionsComponent],
    imports: [CommonModule, ArtemisSharedModule, ArtemisMarkdownEditorModule],
})
export class StructuredGradingInstructionsModule {}
