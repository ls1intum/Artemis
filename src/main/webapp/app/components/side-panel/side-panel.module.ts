import { NgModule } from '@angular/core';

import { ArtemisSharedModule } from 'app/shared/shared.module';
import { SidePanelComponent } from './side-panel.component';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [SidePanelComponent],
    exports: [SidePanelComponent],
})
export class ArtemisSidePanelModule {}
