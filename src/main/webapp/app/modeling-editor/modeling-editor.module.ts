import { ModelingEditorComponent } from './modeling-editor.component';
import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from '../shared';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [ModelingEditorComponent],
    exports: [ModelingEditorComponent],
})
export class ArtemisModelingEditorModule {}
