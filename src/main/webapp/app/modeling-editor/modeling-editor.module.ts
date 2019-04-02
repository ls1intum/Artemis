import { ModelingEditorComponent } from './modeling-editor.component';
import { NgModule } from '@angular/core';
import { ArTEMiSSharedModule } from '../shared';

@NgModule({
    imports: [ArTEMiSSharedModule],
    declarations: [ModelingEditorComponent],
    exports: [ModelingEditorComponent],
})
export class ArTEMiSModelingEditorModule {}
