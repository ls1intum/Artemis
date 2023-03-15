import { NgModule } from '@angular/core';

import { ModelingEditorComponent } from './modeling-editor.component';
import { ModelingExplanationEditorComponent } from './modeling-explanation-editor.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [ModelingEditorComponent, ModelingExplanationEditorComponent],
    exports: [ModelingEditorComponent, ModelingExplanationEditorComponent],
})
export class ArtemisModelingEditorModule {}
