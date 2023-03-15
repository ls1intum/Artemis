import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';

import { GradingInstructionsDetailsComponent } from 'app/exercises/shared/structured-grading-criterion/grading-instructions-details/grading-instructions-details.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    declarations: [GradingInstructionsDetailsComponent],
    exports: [GradingInstructionsDetailsComponent],
    imports: [CommonModule, ArtemisSharedModule, ArtemisMarkdownModule, ArtemisMarkdownEditorModule, ArtemisSharedComponentModule],
})
export class StructuredGradingCriterionModule {}
