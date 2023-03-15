import { NgModule } from '@angular/core';

import { GuidedTourComponent } from './guided-tour.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    declarations: [GuidedTourComponent],
    imports: [ArtemisSharedModule],
    exports: [GuidedTourComponent],
})
export class GuidedTourModule {}
