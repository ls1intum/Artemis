import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { GradingInstructionsDetailsComponent } from 'app/exercises/shared/structured-grading-criterion/grading-instructions-details/grading-instructions-details.component';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';

@NgModule({
    declarations: [GradingInstructionsDetailsComponent],
    exports: [GradingInstructionsDetailsComponent],
    imports: [CommonModule, ArtemisSharedModule, ArtemisMarkdownModule, ArtemisMarkdownEditorModule, ArtemisSharedComponentModule],
})
export class StructuredGradingCriterionModule {}
