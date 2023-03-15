import { NgModule } from '@angular/core';

import { SidePanelComponent } from './side-panel.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [SidePanelComponent],
    exports: [SidePanelComponent],
})
export class ArtemisSidePanelModule {}
