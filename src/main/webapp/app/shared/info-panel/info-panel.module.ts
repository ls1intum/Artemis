import { NgModule } from '@angular/core';

import { ArtemisSharedModule } from 'app/shared/shared.module';
import { InfoPanelComponent } from './info-panel.component';

@NgModule({
    imports: [ArtemisSharedModule, InfoPanelComponent],
    exports: [InfoPanelComponent],
})
export class ArtemisInfoPanelModule {}
