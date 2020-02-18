import { NgModule } from '@angular/core';

import { ArtemisSharedModule } from 'app/shared/shared.module';
import { SlideToggleComponent } from 'app/components/slide-toggle/slide-toggle.component';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [SlideToggleComponent],
    exports: [SlideToggleComponent],
})
export class ArtemisSlideToggleModule {}
