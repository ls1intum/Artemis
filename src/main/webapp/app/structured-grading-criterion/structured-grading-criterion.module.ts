import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ArtemisMarkdownEditorModule } from 'app/markdown-editor';
import { ArtemisSharedModule } from 'app/shared';
import { GradingInstructionDetailComponent } from 'app/structured-grading-criterion/grading-instruction-detail/grading-instruction-detail.component';
import { EditStructuredGradingInstructionComponent } from 'app/structured-grading-criterion/edit-structured-grading-instruction/edit-structured-grading-instruction.component';

@NgModule({
    declarations: [GradingInstructionDetailComponent, EditStructuredGradingInstructionComponent],
    exports: [GradingInstructionDetailComponent, EditStructuredGradingInstructionComponent],
    imports: [CommonModule, ArtemisSharedModule, ArtemisMarkdownEditorModule],
})
export class StructuredGradingCriterionModule {}
