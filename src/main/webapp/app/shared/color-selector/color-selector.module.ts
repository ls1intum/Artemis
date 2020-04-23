import { NgModule } from '@angular/core';

import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ColorSelectorComponent } from './color-selector.component';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [ColorSelectorComponent],
    exports: [ColorSelectorComponent],
})
export class ArtemisColorSelectorModule {}
