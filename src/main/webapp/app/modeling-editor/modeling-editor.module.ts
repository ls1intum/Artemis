import { ModelingEditorComponent } from './modeling-editor.component';
import { NgModule } from '@angular/core';
import { ArTEMiSSharedModule } from '../shared';
import { ModelingEditorDialogComponent } from 'app/modeling-editor/modeling-editor-dialog.component';

@NgModule({
    imports: [ArTEMiSSharedModule],
    declarations: [ModelingEditorComponent, ModelingEditorDialogComponent],
    exports: [ModelingEditorComponent, ModelingEditorDialogComponent],
})
export class ArTEMiSModelingEditorModule {}
