import { NgModule } from '@angular/core';
import { TableEditableCheckboxComponent, TableEditableFieldComponent } from './';
import { ArtemisSharedLibsModule } from 'app/shared';

@NgModule({
    imports: [ArtemisSharedLibsModule],
    declarations: [TableEditableFieldComponent, TableEditableCheckboxComponent],
    exports: [TableEditableFieldComponent, TableEditableCheckboxComponent],
})
export class ArtemisTableModule {}
