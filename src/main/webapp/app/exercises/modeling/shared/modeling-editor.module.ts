import { ModelingEditorComponent } from './modeling-editor.component';
import { NgModule } from '@angular/core';

import { ModelingExplanationEditorComponent } from './modeling-explanation-editor.component';

@NgModule({
    imports: [ModelingEditorComponent, ModelingExplanationEditorComponent],
    exports: [ModelingEditorComponent, ModelingExplanationEditorComponent],
})
export class ArtemisModelingEditorModule {}
