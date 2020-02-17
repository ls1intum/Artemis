import { NgModule } from '@angular/core';
import { TableEditableFieldComponent } from 'app/components/table/table-editable-field.component';
import { ArtemisSharedLibsModule } from 'app/shared/shared-libs.module';
import { TableEditableCheckboxComponent } from 'app/components/table/table-editable-checkbox.component';

@NgModule({
    imports: [ArtemisSharedLibsModule],
    declarations: [TableEditableFieldComponent, TableEditableCheckboxComponent],
    exports: [TableEditableFieldComponent, TableEditableCheckboxComponent],
})
export class ArtemisTableModule {}
