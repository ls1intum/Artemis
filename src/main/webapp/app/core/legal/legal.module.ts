import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';

import { ImprintComponent } from 'app/core/legal/imprint.component';
import { LegalRoutingModule } from 'app/core/legal/legal-routing.module';
import { PrivacyComponent } from 'app/core/legal/privacy.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    declarations: [PrivacyComponent, ImprintComponent],
    imports: [CommonModule, ArtemisSharedModule, LegalRoutingModule],
})
export class ArtemisLegalModule {}
