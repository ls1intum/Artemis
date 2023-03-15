import { NgModule } from '@angular/core';

import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ExportButtonComponent } from 'app/shared/export/export-button.component';
import { ExportModalComponent } from 'app/shared/export/export-modal.component';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';

@NgModule({
    imports: [ArtemisSharedComponentModule, ArtemisSharedCommonModule],
    declarations: [ExportModalComponent, ExportButtonComponent],
    exports: [ExportModalComponent, ExportButtonComponent],
})
export class ExportModule {}
