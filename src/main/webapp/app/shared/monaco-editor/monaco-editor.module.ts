import { NgModule } from '@angular/core';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';
import { MonacoDiffEditorComponent } from 'app/shared/monaco-editor/monaco-diff-editor.component';

@NgModule({
    declarations: [MonacoEditorComponent, MonacoDiffEditorComponent],
    exports: [MonacoEditorComponent, MonacoDiffEditorComponent],
})
export class MonacoEditorModule {}
