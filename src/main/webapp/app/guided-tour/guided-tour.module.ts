import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { GuidedTourComponent } from './guided-tour.component';
import { DeviceDetectorService } from 'ngx-device-detector';

@NgModule({
    declarations: [GuidedTourComponent],
    imports: [ArtemisSharedModule],
    exports: [GuidedTourComponent],
    entryComponents: [GuidedTourComponent],
    providers: [DeviceDetectorService],
})
export class GuidedTourModule {}
