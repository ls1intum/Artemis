import { MarkdownEditorComponent } from './markdown-editor.component';
import { NgModule } from '@angular/core';
import { ArTEMiSSharedModule } from '../shared';

@NgModule({
    imports: [
        ArTEMiSSharedModule,
    ],
    declarations: [
        MarkdownEditorComponent
    ],
    entryComponents: [
        MarkdownEditorComponent,
   ]
})
export class ArTEMiSMarkdownEditorModule {}
