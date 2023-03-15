import { NgModule } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { MarkdownEditorComponent } from './markdown-editor.component';
import { ArtemisColorSelectorModule } from 'app/shared/color-selector/color-selector.module';
import { AceEditorModule } from 'app/shared/markdown-editor/ace-editor/ace-editor.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [ArtemisSharedModule, AceEditorModule, FormsModule, ArtemisColorSelectorModule],
    declarations: [MarkdownEditorComponent],
    exports: [MarkdownEditorComponent],
})
export class ArtemisMarkdownEditorModule {}
