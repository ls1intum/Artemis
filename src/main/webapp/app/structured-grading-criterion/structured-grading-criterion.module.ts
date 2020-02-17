import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { GradingInstructionDetailComponent } from 'app/structured-grading-criterion/grading-instruction-detail/grading-instruction-detail.component';
import { EditStructuredGradingInstructionComponent } from 'app/structured-grading-criterion/edit-structured-grading-instruction/edit-structured-grading-instruction.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisMarkdownEditorModule } from 'app/markdown-editor/markdown-editor.module';

@NgModule({
    declarations: [GradingInstructionDetailComponent, EditStructuredGradingInstructionComponent],
    exports: [GradingInstructionDetailComponent, EditStructuredGradingInstructionComponent],
    imports: [CommonModule, ArtemisSharedModule, ArtemisMarkdownEditorModule],
})
export class StructuredGradingCriterionModule {}
