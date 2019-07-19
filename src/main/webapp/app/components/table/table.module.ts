import { NgModule } from '@angular/core';
import { TableEditableFieldComponent } from './';
import { ArTEMiSSharedLibsModule } from 'app/shared';

@NgModule({
    imports: [ArTEMiSSharedLibsModule],
    declarations: [TableEditableFieldComponent],
    exports: [TableEditableFieldComponent],
})
export class ArtemisTableModule {}
