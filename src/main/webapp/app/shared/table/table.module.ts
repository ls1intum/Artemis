import { NgModule } from '@angular/core';
import { TableEditableFieldComponent } from 'app/shared/table/table-editable-field.component';

import { TableEditableCheckboxComponent } from 'app/shared/table/table-editable-checkbox.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [ArtemisSharedModule, TableEditableFieldComponent, TableEditableCheckboxComponent],
    exports: [TableEditableFieldComponent, TableEditableCheckboxComponent],
})
export class ArtemisTableModule {}
