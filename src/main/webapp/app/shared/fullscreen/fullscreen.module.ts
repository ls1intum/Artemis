import { FullscreenComponent } from 'app/shared/fullscreen/fullscreen.component';
import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [FullscreenComponent],
    exports: [FullscreenComponent],
})
export class ArtemisFullscreenModule {}
