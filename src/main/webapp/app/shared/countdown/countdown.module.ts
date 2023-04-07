import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { CountdownComponent } from 'app/shared/countdown/countdown.component';
import { CountdownOverlayComponent } from './countdown-overlay.component';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [CountdownComponent, CountdownOverlayComponent],
    exports: [CountdownComponent, CountdownOverlayComponent],
})
export class ArtemisCountdownModule {}
