import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ArtemisSharedModule } from 'app/shared/shared.module';

import { LegalRoutingModule } from 'app/core/legal/legal-routing.module';
import { PrivacyComponent } from 'app/core/legal/privacy.component';
import { ImprintComponent } from 'app/core/legal/imprint.component';
import { DataExportComponent } from 'app/core/legal/data-export/data-export.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';

@NgModule({
    declarations: [PrivacyComponent, ImprintComponent, DataExportComponent],
    imports: [CommonModule, ArtemisSharedModule, LegalRoutingModule, ArtemisSharedComponentModule],
})
export class ArtemisLegalModule {}
