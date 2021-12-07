import { NgModule } from '@angular/core';
import { AceEditorComponent } from 'app/shared/markdown-editor/ace-editor/ace-editor.component';

@NgModule({
    declarations: [AceEditorComponent],
    exports: [AceEditorComponent],
})
export class AceEditorModule {}
