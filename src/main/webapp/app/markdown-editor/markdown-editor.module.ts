import { MarkdownEditorComponent } from './markdown-editor.component';
import { NgModule } from '@angular/core';
import { ArTEMiSSharedModule } from '../shared';
import { AceEditorModule } from 'ng2-ace-editor';

@NgModule({
    imports: [ArTEMiSSharedModule, AceEditorModule],
    declarations: [MarkdownEditorComponent],
    exports: [MarkdownEditorComponent]
})
export class ArTEMiSMarkdownEditorModule {}
