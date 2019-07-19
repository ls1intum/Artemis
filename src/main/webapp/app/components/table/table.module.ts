import { NgModule } from '@angular/core';
import { TableEditableCheckboxComponent, TableEditableFieldComponent } from './';
import { ArTEMiSSharedLibsModule } from 'app/shared';

@NgModule({
    imports: [ArTEMiSSharedLibsModule],
    declarations: [TableEditableFieldComponent, TableEditableCheckboxComponent],
    exports: [TableEditableFieldComponent, TableEditableCheckboxComponent],
})
export class ArtemisTableModule {}
