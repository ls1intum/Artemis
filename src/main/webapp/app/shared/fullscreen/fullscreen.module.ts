import { NgModule } from '@angular/core';

import { FullscreenComponent } from 'app/shared/fullscreen/fullscreen.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [FullscreenComponent],
    exports: [FullscreenComponent],
})
export class ArtemisFullscreenModule {}
