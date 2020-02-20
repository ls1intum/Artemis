import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ArtemisSharedModule } from 'app/shared/shared.module';

import { LegalRoutingModule } from 'app/core/legal/legal-routing.module';
import { PrivacyComponent } from 'app/core/legal/privacy/privacy.component';

@NgModule({
    declarations: [PrivacyComponent],
    imports: [CommonModule, ArtemisSharedModule, LegalRoutingModule],
})
export class ArtemisLegalModule {}
