import { NgModule } from '@angular/core';

import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ColorSelectorComponent } from './color-selector.component';

@NgModule({
    imports: [ArtemisSharedModule, ColorSelectorComponent],
    exports: [ColorSelectorComponent],
})
export class ArtemisColorSelectorModule {}
