import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { DataExportRoutingModule } from 'app/core/legal/data-export/data-export-routing.module';
import { DataExportComponent } from 'app/core/legal/data-export/data-export.component';

@NgModule({
    declarations: [DataExportComponent],
    imports: [CommonModule, ArtemisSharedComponentModule, ArtemisSharedModule, DataExportRoutingModule],
})
export class ArtemisDataExportModule {}
