import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ArtemisSharedModule } from 'app/shared/shared.module';

import { PrivacyComponent } from 'app/core/legal/privacy.component';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { PrivacyRoutingModule } from 'app/core/legal/privacy-routing.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { DataExportComponent } from 'app/core/legal/data-export/data-export.component';

@NgModule({
    declarations: [PrivacyComponent, DataExportComponent],
    imports: [CommonModule, ArtemisSharedComponentModule, ArtemisSharedModule, PrivacyRoutingModule, ArtemisMarkdownModule],
})
export class ArtemisPrivacyModule {}
