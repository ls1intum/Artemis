import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ArtemisSharedModule } from 'app/shared/shared.module';

import { PrivacyComponent } from 'app/core/legal/privacy.component';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { PrivacyRoutingModule } from 'app/core/legal/privacy-routing.module';

@NgModule({
    declarations: [PrivacyComponent],
    imports: [CommonModule, ArtemisSharedModule, PrivacyRoutingModule, ArtemisMarkdownModule],
})
export class ArtemisPrivacyModule {}
