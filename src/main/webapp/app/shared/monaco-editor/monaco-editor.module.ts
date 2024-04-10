import { NgModule } from '@angular/core';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';

@NgModule({
    declarations: [MonacoEditorComponent],
    exports: [MonacoEditorComponent],
})
export class MonacoEditorModule {}
