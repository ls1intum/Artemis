import { NgModule } from '@angular/core';

import { ArtemisSharedLibsModule } from 'app/shared/shared-libs.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { TableEditableCheckboxComponent } from 'app/shared/table/table-editable-checkbox.component';
import { TableEditableFieldComponent } from 'app/shared/table/table-editable-field.component';

@NgModule({
    imports: [ArtemisSharedLibsModule, ArtemisSharedModule],
    declarations: [TableEditableFieldComponent, TableEditableCheckboxComponent],
    exports: [TableEditableFieldComponent, TableEditableCheckboxComponent],
})
export class ArtemisTableModule {}
