import { NgModule } from '@angular/core';

import { InfoPanelComponent } from './info-panel.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [InfoPanelComponent],
    exports: [InfoPanelComponent],
})
export class ArtemisInfoPanelModule {}
