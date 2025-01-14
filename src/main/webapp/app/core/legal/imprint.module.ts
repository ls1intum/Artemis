import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ArtemisSharedModule } from 'app/shared/shared.module';

import { ImprintRoutingModule } from 'app/core/legal/imprint-routing.module';
import { ImprintComponent } from 'app/core/legal/imprint.component';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';

@NgModule({
    imports: [CommonModule, ArtemisSharedModule, ImprintRoutingModule, ArtemisMarkdownModule, ImprintComponent],
})
export class ArtemisImprintModule {}
