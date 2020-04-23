import { ModelingEditorComponent } from './modeling-editor.component';
import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [ModelingEditorComponent],
    exports: [ModelingEditorComponent],
})
export class ArtemisModelingEditorModule {}
