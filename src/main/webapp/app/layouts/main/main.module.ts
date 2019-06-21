import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';

import { ArTEMiSSharedModule } from 'app/shared';
import { JhiMainComponent } from 'app/layouts';

@NgModule({
    imports: [ArTEMiSSharedModule],
    declarations: [JhiMainComponent],
    entryComponents: [],
    exports: [JhiMainComponent],
    schemas: [CUSTOM_ELEMENTS_SCHEMA],
})
export class ArTEMiSMainModule {}
