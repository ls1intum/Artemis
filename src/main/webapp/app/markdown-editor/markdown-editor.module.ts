import { MarkdownEditorComponent } from './markdown-editor.component';
import { NgModule } from '@angular/core';
import { ArTEMiSSharedModule } from '../shared';
import { AceEditorModule } from 'ng2-ace-editor';
import { FormsModule } from '@angular/forms';

@NgModule({
    imports: [ArTEMiSSharedModule, AceEditorModule, FormsModule],
    declarations: [MarkdownEditorComponent],
    exports: [MarkdownEditorComponent]
})
export class ArTEMiSMarkdownEditorModule {}
