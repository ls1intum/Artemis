import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';

import { ArtemisSharedModule } from 'app/shared';
import { ColorSelectorComponent } from './color-selector.component';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [ColorSelectorComponent],
    exports: [ColorSelectorComponent],
    schemas: [CUSTOM_ELEMENTS_SCHEMA],
})
export class ArtemisColorSelectorModule {}
