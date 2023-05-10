import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ArtemisSharedModule } from 'app/shared/shared.module';

import { ImprintRoutingModule } from 'app/core/legal/imprint-routing.module';
import { ImprintComponent } from 'app/core/legal/imprint.component';

@NgModule({
    declarations: [ImprintComponent],
    imports: [CommonModule, ArtemisSharedModule, ImprintRoutingModule],
    exports: [ImprintComponent],
})
export class ArtemisImprintModule {}
