import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ArtemisSharedModule } from 'app/shared/shared.module';

import { LegalRoutingModule } from 'app/core/legal/legal-routing.module';
import { PrivacyComponent } from 'app/core/legal/privacy.component';
import { ImprintComponent } from 'app/core/legal/imprint.component';

@NgModule({
    declarations: [PrivacyComponent, ImprintComponent],
    imports: [CommonModule, ArtemisSharedModule, LegalRoutingModule],
})
export class ArtemisLegalModule {}
