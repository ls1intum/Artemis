import { MarkdownEditorComponent } from './markdown-editor.component';
import { NgModule } from '@angular/core';
import { AceEditorModule } from 'app/shared/markdown-editor/ace-editor/ace-editor.module';
import { FormsModule } from '@angular/forms';
import { ArtemisColorSelectorModule } from 'app/shared/color-selector/color-selector.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { MatMenuModule } from '@angular/material/menu';
import { MatButtonModule } from '@angular/material/button';

@NgModule({
    imports: [ArtemisSharedModule, AceEditorModule, FormsModule, ArtemisColorSelectorModule, MatMenuModule, MatButtonModule],
    declarations: [MarkdownEditorComponent],
    exports: [MarkdownEditorComponent],
})
export class ArtemisMarkdownEditorModule {}
