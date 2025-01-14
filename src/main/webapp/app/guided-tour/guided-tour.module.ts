import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { GuidedTourComponent } from './guided-tour.component';

@NgModule({
    imports: [ArtemisSharedModule, GuidedTourComponent],
    exports: [GuidedTourComponent],
})
export class GuidedTourModule {}
