import { NgModule } from '@angular/core';
import { AceEditorComponent } from 'app/shared/markdown-editor/ace-editor/ace-editor.component';
import { AceEditorDirective } from 'app/shared/markdown-editor/ace-editor/ace-editor.directive';

@NgModule({
    declarations: [AceEditorComponent, AceEditorDirective],
    exports: [AceEditorComponent, AceEditorDirective],
})
export class AceEditorModule {}
