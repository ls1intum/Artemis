import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';

import { ArTEMiSSharedModule } from 'app/shared';
import { SidePanelComponent } from './side-panel.component';

@NgModule({
    imports: [ArTEMiSSharedModule],
    declarations: [SidePanelComponent],
    exports: [SidePanelComponent],
    schemas: [CUSTOM_ELEMENTS_SCHEMA],
})
export class ArTEMiSSidePanelModule {}
