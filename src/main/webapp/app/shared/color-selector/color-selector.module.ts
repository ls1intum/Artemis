import { NgModule } from '@angular/core';

import { ColorSelectorComponent } from './color-selector.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [ColorSelectorComponent],
    exports: [ColorSelectorComponent],
})
export class ArtemisColorSelectorModule {}
