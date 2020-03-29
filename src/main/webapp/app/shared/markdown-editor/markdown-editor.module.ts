import { MarkdownEditorComponent } from './markdown-editor.component';
import { NgModule } from '@angular/core';
import { AceEditorModule } from 'ngx-ace-editor-wrapper';
import { FormsModule } from '@angular/forms';
import { ArtemisColorSelectorModule } from 'app/shared/color-selector/color-selector.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [ArtemisSharedModule, AceEditorModule, FormsModule, ArtemisColorSelectorModule],
    declarations: [MarkdownEditorComponent],
    exports: [MarkdownEditorComponent],
})
export class ArtemisMarkdownEditorModule {}
