import { CsvExportModalComponent } from 'app/shared/export/csv-export-modal.component';
import { CsvExportButtonComponent } from 'app/shared/export/csv-export-button.component';
import { NgModule } from '@angular/core';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';

@NgModule({
    imports: [ArtemisSharedComponentModule, ArtemisSharedCommonModule],
    entryComponents: [],
    declarations: [CsvExportModalComponent, CsvExportButtonComponent],
    exports: [CsvExportModalComponent, CsvExportButtonComponent],
})
export class CsvExportModule {}
