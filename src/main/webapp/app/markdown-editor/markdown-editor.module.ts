import { MarkdownEditorComponent } from './markdown-editor.component';
import { NgModule } from '@angular/core';
import { ArTEMiSSharedModule } from '../shared';
import { AceEditorModule } from 'ng2-ace-editor';
import { FormsModule } from '@angular/forms';
import { ArTEMiSColorSelectorModule } from 'app/components/color-selector/color-selector.module';

@NgModule({
    imports: [ArTEMiSSharedModule, AceEditorModule, FormsModule, ArTEMiSColorSelectorModule],
    declarations: [MarkdownEditorComponent],
    exports: [MarkdownEditorComponent],
})
export class ArTEMiSMarkdownEditorModule {}
