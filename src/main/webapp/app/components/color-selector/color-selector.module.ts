import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';

import { ArTEMiSSharedModule } from 'app/shared';
import { ColorSelectorComponent } from './color-selector.component';

@NgModule({
    imports: [ArTEMiSSharedModule],
    declarations: [
        ColorSelectorComponent
    ],
    exports: [
        ColorSelectorComponent
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTEMiSColorSelectorModule {
}
